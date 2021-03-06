/*
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.tdk.signaturetest.merge;

import com.sun.tdk.signaturetest.Result;
import com.sun.tdk.signaturetest.core.AppContext;
import com.sun.tdk.signaturetest.core.Erasurator;
import com.sun.tdk.signaturetest.core.Log;
import com.sun.tdk.signaturetest.core.context.MergeOptions;
import com.sun.tdk.signaturetest.core.context.Option;
import com.sun.tdk.signaturetest.loaders.VirtualClassDescriptionLoader;
import com.sun.tdk.signaturetest.model.*;
import com.sun.tdk.signaturetest.sigfile.FeaturesHolder;
import com.sun.tdk.signaturetest.util.I18NResourceBundle;
import com.sun.tdk.signaturetest.util.SwissKnife;

import java.text.MessageFormat;
import java.util.*;

/**
 * Merges some APIs according JSR68 rules
 *
 * @author Mikhail Ershov
 * @author Roman Makarchuk
 */
public class JSR68Merger extends FeaturesHolder {

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(JSR68Merger.class);
    private final MergeOptions mo = AppContext.getContext().getBean(MergeOptions.class);

    private final Log log;
    private final Result result;
    private final Erasurator erasurator;

    public JSR68Merger(Log log, Result result, FeaturesHolder fh) {
        this.log = log;
        this.result = result;
        erasurator = new Erasurator();
        setFeatures(fh.getSupportedFeatures());
    }

    public VirtualClassDescriptionLoader merge(MergedSigFile[] files) {
        //this.mode = mode;
        VirtualClassDescriptionLoader result = new VirtualClassDescriptionLoader();

        for (int i = 0; i < files.length; i++) {
            MergedSigFile mf = files[i];
            for (ClassDescription cd : mf.getClassSet().values()) {
                // If one of input APIs contain an element and other doesn't,
                // this element goes to the result API without modification except for the following case :

                // 1) is it unique?
                boolean unique = true;
                ArrayList<ClassDescription> sameClasses = new ArrayList<>();
                ArrayList<MergedSigFile> filesForSameClasses = new ArrayList<>();
                sameClasses.add(cd);
                filesForSameClasses.add(mf);
                for (int j = 0; j < files.length; j++) {
                    if (i == j) {
                        continue;
                    }
                    MergedSigFile mfOther = files[j];
                    if (mfOther.getClassSet().containsKey(cd.getQualifiedName())) {
                        unique = false;
                        sameClasses.add(mfOther.getClassSet().get(cd.getQualifiedName()));
                        filesForSameClasses.add(mfOther);
                    }
                }

                // If this element is first declared class member in inheritance
                // chain and the other API inherits the same element, then this element doesn't
                // go to the result API.
                if (unique) {
                    result.add(cd);
                } else {
                    //                    logger.fine("Not unique, to merge " + cd.getQualifiedName());
                    ClassDescription resultedClass = new ClassDescription();
                    resultedClass.setupClassName(cd.getQualifiedName());

                    ClassDescription[] classes = sameClasses.toArray(new ClassDescription[0]);
                    MergedSigFile[] filesForClasses = filesForSameClasses.toArray(new MergedSigFile[0]);
                    if (merge(classes, resultedClass, filesForClasses) && merge2(classes, resultedClass)) {
                        result.add(resultedClass);
                    }
                }
            }
        }

        Iterator<ClassDescription> it = result.getClassIterator();
        List<ClassDescription> innersToRemove = new ArrayList<>();
        nextClass:
        while (it.hasNext()) {
            ClassDescription cd = it.next();
            try {
                result.load(cd.getPackageName());
                error(i18n.getString("Merger.error.packageconflict", cd.getPackageName()));
            } catch (ClassNotFoundException e) {
                // this is normal that there is no classes
                // with package name
            }
            if (cd.getQualifiedName().contains("$")) {
                try {
                    ClassDescription outer = result.load(cd.getDeclaringClassName());
                    InnerDescr[] dc = outer.getDeclaredClasses();
                    for (InnerDescr innerDescr : dc) {
                        if (innerDescr.getQualifiedName().equals(cd.getQualifiedName())) {
                            continue nextClass;
                        }
                    }

                    for (Iterator<ClassDescription> it2 = result.getClassIterator(); it2.hasNext(); ) {
                        ClassDescription similarInner = it2.next();
                        if (similarInner.getQualifiedName().contains("$")
                                && similarInner.getName().equals(cd.getName())) {
                            ClassDescription parent = outer;
                            while (true) {
                                try {
                                    parent = result.load(parent.getSuperClass().getQualifiedName());
                                    if (similarInner.getDeclaringClassName().equals(parent.getQualifiedName())) {
                                        for (int i = 0; i < files.length; i++) {
                                            if (files[i].getClassSet().containsKey(cd.getQualifiedName())) {
                                                for (int j = 0; j < files.length; j++) {
                                                    if (files[j].getClassSet().containsKey(similarInner.getQualifiedName())) {
                                                        if (j == i) {
                                                            continue nextClass;
                                                        }
                                                    }
                                                }
                                                innersToRemove.add(cd);
                                                continue nextClass;
                                            }
                                        }

                                    }
                                } catch (Exception e) {
                                    // no parents
                                    break;
                                }
                            }

                        }
                    }

                    InnerDescr d = new InnerDescr();
                    d.setupClassName(cd.getQualifiedName());
                    d.setModifiers(cd.getModifiers());
                    InnerDescr[] newInners = new InnerDescr[dc.length + 1];
                    System.arraycopy(dc, 0, newInners, 0, dc.length);
                    newInners[dc.length] = d;
                    outer.setNestedClasses(newInners);

                } catch (ClassNotFoundException ex) {
                    SwissKnife.reportThrowable(ex);
                }
            }
        }
        it = result.getClassIterator();
        while (it.hasNext()) {
            if (innersToRemove.contains(it.next())) {
                it.remove();
            }
        }

        return result;
    }

    private boolean merge(ClassDescription[] similarClasses, ClassDescription result, MergedSigFile[] sigfiles) {

        boolean mAbs = similarClasses[0].isAbstract();
        for (int i = 1; i < similarClasses.length; i++) {
            if (similarClasses[i].isAbstract() != mAbs) {
                error(similarClasses[0].getQualifiedName() + " " + i18n.getString("Merger.error.modifierconfict", "abstract"));
                return false;
            }
        }

        boolean mInt = similarClasses[0].isInterface();
        for (int i = 1; i < similarClasses.length; i++) {
            if (similarClasses[i].isInterface() != mInt) {
                error(similarClasses[0].getQualifiedName() + i18n.getString("Merger.error.classwithinterface"));
                return false;
            }
        }

        if (!mergeMod(similarClasses, result)) {
            return false;
        }

        if (!mergeSuprs(similarClasses, result, sigfiles)) {
            return false;
        }

        return mergeInterfaces(similarClasses, result);

    }

    private boolean merge2(ClassDescription[] similarClasses, ClassDescription result) {

        if (prepareGenerics(similarClasses, result)) {

            mergeAnnotations(similarClasses, result);
            mergeTypeParameters(similarClasses, result);

            if (!mergeMembers(similarClasses, result)) {
                return false;
            }

            checkGenerics(result);
        }

        return true;
    }

    private void checkGenerics(ClassDescription result) {
        ClassDescription eResult = erasurator.fullErasure(result);
        ConstructorDescr[] genCostr = eResult.getDeclaredConstructors();
        MethodDescr[] genMeth = eResult.getDeclaredMethods();
        FieldDescr[] genFld = eResult.getDeclaredFields();

        Set<String> sims = new HashSet<>();

        for (int i = 0; i < genCostr.length; i++) {
            String s = genCostr[i].getSignature();
            if (sims.contains(s)) {
                error(show(genCostr[i]) + i18n.getString("Merger.error.typeconflict"), new Object[]{result.getDeclaredConstructors()[i], s});
            }
            sims.add(s);
        }

        for (int i = 0; i < genMeth.length; i++) {
            String s = genMeth[i].getSignature();
            if (sims.contains(s)) {
                error(show(genCostr[i]) + i18n.getString("Merger.error.typeconflict"), new Object[]{result.getDeclaredMethods()[i], s});
            }
            sims.add(s);
        }

        for (int i = 0; i < genFld.length; i++) {
            String s = genFld[i].getName();
            if (sims.contains(s)) {
                error(show(genCostr[i]) + i18n.getString("Merger.error.typeconflict"), new Object[]{result.getDeclaredFields()[i], s});
            }
            sims.add(s);
        }

    }

    private boolean prepareGenerics(ClassDescription[] similarClasses, ClassDescription result) {
        ArrayList<ClassDescription> hasGen = new ArrayList<>();
        ArrayList<ClassDescription> noGen = new ArrayList<>();
        int noGenPos = -1;
        int genPos = -1;
        for (int i = 0; i < similarClasses.length; i++) {
            if (isGeneralized(similarClasses[i])) {
                hasGen.add(similarClasses[i]);
                genPos = i;
            } else {
                noGen.add(similarClasses[i]);
                noGenPos = i;
            }
        }
        if (hasGen.isEmpty() || noGen.isEmpty()) {
            return true;
        }

        if (hasGen.size() == 1 && noGen.size() == 1) {
            ClassDescription hasGenCD = similarClasses[genPos];

            ConstructorDescr[] genCostr = hasGenCD.getDeclaredConstructors();
            MethodDescr[] genMeth = hasGenCD.getDeclaredMethods();
            FieldDescr[] genFld = hasGenCD.getDeclaredFields();

            ClassDescription hasGenEraCD = erasurator.fullErasure(hasGenCD);
            ClassDescription noGenCD = similarClasses[noGenPos];
            if (noGenCD.equals(hasGenEraCD)) {
                noGenCD.setTypeParameters(hasGenCD.getTypeParameters());
            }

            ConstructorDescr[] noGenCostr = noGenCD.getDeclaredConstructors();
            ConstructorDescr[] eraCostr = hasGenEraCD.getDeclaredConstructors();

            MethodDescr[] noGenMeth = noGenCD.getDeclaredMethods();
            MethodDescr[] eraMeth = hasGenEraCD.getDeclaredMethods();

            FieldDescr[] noGenFld = noGenCD.getDeclaredFields();
            FieldDescr[] eraFld = hasGenEraCD.getDeclaredFields();

            // constructors
            for (int i = 0; i < noGenCostr.length; i++) {
                ConstructorDescr c = noGenCostr[i];
                for (int j = 0; j < eraCostr.length; j++) {
                    ConstructorDescr c2 = eraCostr[j];
                    ConstructorDescr c3 = genCostr[j];
                    if (c.equals(c2) && !c.equals(c3)) {
                        noGenCD.setConstructor(i, (ConstructorDescr) c3.clone());
                        break;
                    }
                }
            }

            // methods
            for (int i = 0; i < noGenMeth.length; i++) {
                MethodDescr m = noGenMeth[i];
                for (int j = 0; j < eraMeth.length; j++) {
                    MethodDescr m2 = eraMeth[j];
                    MethodDescr m3 = genMeth[j];
                    if (m.getSignature().equals(m2.getSignature()) && m.getType().equals(m2.getType())) {
                        noGenCD.setMethod(i, (MethodDescr) m3.clone());
                        break;
                    }
                }
            }

            // fields
            for (int i = 0; i < noGenFld.length; i++) {
                FieldDescr f = noGenFld[i];
                for (int j = 0; j < eraFld.length; j++) {
                    FieldDescr f2 = eraFld[j];
                    FieldDescr f3 = genFld[j];
                    if (f.equals(f2) && f.getType().equals(f2.getType())) {
                        noGenCD.setField(i, (FieldDescr) f3.clone());
                        break;
                    }
                }
            }
            return true;
        }

        if (hasGen.size() >= 1 || noGen.size() >= 1) {
            ClassDescription res1 = (ClassDescription) result.clone();
            ClassDescription res2 = (ClassDescription) result.clone();
            if (noGen.size() >= 0) {
                merge2(noGen.toArray(new ClassDescription[0]), res1);
            } else {
                res1 = noGen.get(0);
            }
            if (hasGen.size() >= 0) {
                merge2(hasGen.toArray(new ClassDescription[0]), res2);
            } else {
                res1 = hasGen.get(0);
            }
            merge2(new ClassDescription[]{res1, res2}, result);
            return false;
        }

        return true;

    }

    private static boolean isGeneralized(ClassDescription clazz) {
        if (clazz.getTypeParameters() != null) {
            return true;
        }
        Iterator<MemberDescription> it = clazz.getMembersIterator();
        while (it.hasNext()) {
            MemberDescription md = it.next();
            if (!md.getDeclaringClassName().equals(clazz.getQualifiedName())) {
                continue;
            }
            if (md.getTypeParameters() != null) {
                return true;
            }
        }
        return false;
    }

    //  Merge modifiers for two class members and report error, if any.
    //
    private boolean mergeMod(MemberDescription[] x, MemberDescription z) {
        for (MemberDescription x3 : x) {
            z.setModifiers(z.getModifiers() | x3.getModifiers());
        }

        // access modifiers - set more visible
        // clean up
        int visibilityBits = Modifier.PUBLIC.getValue() | Modifier.PROTECTED.getValue() | Modifier.PRIVATE.getValue();
        z.setModifiers(z.getModifiers() & ~(visibilityBits));

        int vis = 0;
        for (MemberDescription x2 : x) {
            vis = vis | (x2.getModifiers() & visibilityBits);
        }

        if ((vis & Modifier.PUBLIC.getValue()) != 0) {
            z.setModifiers(z.getModifiers() | Modifier.PUBLIC.getValue());
        } else if ((vis & Modifier.PROTECTED.getValue()) != 0) {
            z.setModifiers(z.getModifiers() | Modifier.PROTECTED.getValue());
        } else if ((vis & Modifier.PRIVATE.getValue()) != 0) {
            /* nothing */
        } else {
            z.setModifiers(z.getModifiers() | Modifier.PRIVATE.getValue());
        }

        // "final" modifier
        // If the elements differ in the "final" modifier, don't include it.
        // Note that if class is final, then all its methods are implicitly final (JLS II, 8.4.3.3).
        for (MemberDescription x1 : x) {
            if (!x1.isFinal()) {
                z.setModifiers(z.getModifiers() & ~Modifier.FINAL.getValue());
                break;
            }
        }

        // "static" modifier
        // If the elements differ in the "static" modifier, declare conflict.
        boolean mStat = x[0].isStatic();
        for (int i = 1; i < x.length; i++) {
            if (x[i].isStatic() != mStat) {
                error(show(x[0]) + " " + i18n.getString("Merger.error.modifierconfict", "static"));
                return false;
            }
        }

        return true;
    }

    // If superclass of c1 is subclass of superclass of c2,
    // use superclass of c1 as superclass for the new element.
    // Otherwise, if superclass of c2 is subclass of superclass
    // of c1, use superclass of c2 as superclass for the new
    // element.
    // Otherwise declare conflict
    private boolean mergeSuprs(ClassDescription[] similarClasses, ClassDescription result, MergedSigFile[] sigfiles) {
        ArrayList<SuperClass> superclasses = new ArrayList<>();
        // collect superclasses
        for (ClassDescription similarClass : similarClasses) {
            SuperClass sc = similarClass.getSuperClass();
            if (sc != null && !superclasses.contains(sc)) {
                superclasses.add(sc);
            }
        }

        // not superclasses at all (Object)
        if (superclasses.isEmpty()) {
            return true;
        }

        // similar or (null and similar)
        if (superclasses.size() == 1) {
            result.setSuperClass(superclasses.iterator().next());
            return true;
        }

        for (MergedSigFile file : sigfiles) {
            // 1) find a file which contains all superclasses
            boolean all = true;
            for (SuperClass sc : superclasses) {
                if (!file.getClassSet().containsKey(sc.getQualifiedName())) {
                    all = false;
                    break;
                }
            }
            if (!all) {
                continue;  // try the next sigfile
            }
            for (int j = 0; j < superclasses.size(); j++) {
                SuperClass scSub = superclasses.get(j);
                boolean subSuperFound = true;
                for (int k = 0; k < superclasses.size(); k++) {
                    try {
                        if (k == j) {
                            continue;
                        }
                        SuperClass scSuper = superclasses.get(k);

                        if (!file.getClassHierarchy().isSubclass(scSub.getQualifiedName(),
                                scSuper.getQualifiedName())) {
                            subSuperFound = false;
                            break;
                        }
                    } catch (ClassNotFoundException ex) {
                        SwissKnife.reportThrowable(ex);
                    }
                }
                if (subSuperFound) {
                    result.setSuperClass(scSub);
                    return true;
                }
            }
        }

        error(show(result) + " " + i18n.getString("Merger.error.superclassesnotrelated"));
        System.out.println("Can't merge superclasses");
        return false;
    }

    private static boolean mergeInterfaces(ClassDescription[] similarClasses, ClassDescription result) {

        Set<SuperInterface> ts = new TreeSet<>(new Comparator<SuperInterface>() {
            public int compare(SuperInterface s1, SuperInterface s2) {
                return s1.getQualifiedName().compareTo(s2.getQualifiedName());
            }
        });

        ts.addAll(Arrays.asList(similarClasses[0].getInterfaces()));
        for (int i = 1; i < similarClasses.length; i++) {
            ts.addAll(Arrays.asList(similarClasses[i].getInterfaces()));
        }

        result.createInterfaces(ts.size());
        Iterator<SuperInterface> it = ts.iterator();
        int i = 0;
        while (it.hasNext()) {
            SuperInterface si = (SuperInterface) (it.next()).clone();
            si.setDirect(true);
            result.setInterface(i++, si);
        }

        return true;
    }

    private boolean mergeMembers(ClassDescription[] similarClasses, ClassDescription result) {

        // methods
        makeMethods(similarClasses, result);

        // constructors
        makeCtors(similarClasses, result);

        // fields
        makeFields(similarClasses, result);

        // annotations
        // hiders
        makeHiders(similarClasses, result);

        return true;
    }

    private static boolean makeHiders(ClassDescription[] similarClasses, ClassDescription result) {
        // result should be intersection
        Set<String> internalFields = new HashSet<>(similarClasses[0].getInternalFields());
        Set<String> internalClasses = new HashSet<>(similarClasses[0].getInternalClasses());
        Set<String> xFields = new HashSet<>(similarClasses[0].getXFields());
        Set<String> xClasses = new HashSet<>(similarClasses[0].getXClasses());
        for (int i = 1; i < similarClasses.length; i++) {
            internalFields.retainAll(similarClasses[i].getInternalFields());
            internalClasses.retainAll(similarClasses[i].getInternalClasses());
            xFields.retainAll(similarClasses[i].getXFields());
            xClasses.retainAll(similarClasses[i].getXClasses());
        }
        result.setInternalClasses(internalClasses);
        result.setInternalFields(internalFields);
        result.setXFields(xFields);
        result.setXClasses(xFields);

        return true;
    }

    private boolean makeFields(ClassDescription[] similarClasses, ClassDescription result) {
        ArrayList<FieldDescr> fields = new ArrayList<>();
        Set<String> h = new HashSet<>();
        for (int i = 0; i < similarClasses.length; i++) {
            FieldDescr[] fds = similarClasses[i].getDeclaredFields();
            for (FieldDescr fd : fds) {
                if (!isFeatureSupported(FeaturesHolder.NonStaticConstants) && !fd.isStatic()) {
                    fd.setConstantValue(null);
                }

                ArrayList<FieldDescr> sameFields = new ArrayList<>();
                sameFields.add(fd);
                boolean isUnique = true;
                for (int k = 0; k < similarClasses.length; k++) {
                    if (k == i) {
                        continue;
                    }
                    FieldDescr[] fd2 = similarClasses[k].getDeclaredFields();
                    for (FieldDescr fieldDescr : fd2) {
                        if (fieldDescr.getName().equals(fd.getName())) {
                            isUnique = false;
                            sameFields.add(fieldDescr);
                        }
                    }
                }
                if (isUnique) {
                    if (!h.contains(fd.getName())) {
                        fields.add(fd);
                    }
                    h.add(fd.getName());
                } else {
                    // some merge
                    FieldDescr f = new FieldDescr();
                    if (mergeFields(sameFields.toArray(FieldDescr.EMPTY_ARRAY), f)) {
                        if (!h.contains(f.getName())) {
                            fields.add(f);
                        }
                        h.add(f.getName());
                    } else {
                        // ??
                    }
                }
            }
        }

        result.setFields(fields.toArray(FieldDescr.EMPTY_ARRAY));
        return true;
    }

    private boolean mergeFields(FieldDescr[] similarFileds, FieldDescr result) {
        String type = similarFileds[0].getType();
        String value = similarFileds[0].getConstantValue();
        for (int i = 1; i < similarFileds.length; i++) {
            if (!type.equals(similarFileds[i].getType())) {
                error(show(similarFileds[0]) + i18n.getString("Merger.error.typeconflict"), new Object[]{type, similarFileds[i].getType()});
                return false;
            }
            String anotherValue = similarFileds[i].getConstantValue();
            if (!(value == null && anotherValue == null)) {
                if ((value == null || anotherValue == null) || !value.equals(anotherValue)) {
                    error(show(similarFileds[0]) + i18n.getString("Merger.error.differentvalues"), new Object[]{value, anotherValue});
                    return false;
                }
            }

        }

        if (!mergeMod(similarFileds, result)) {
            return false;
        }

        result.setupMemberName(similarFileds[0].getQualifiedName());
        result.setType(type);
        result.setConstantValue(value);
        mergeAnnotations(similarFileds, result);
        mergeTypeParameters(similarFileds, result);

        return true;
    }

    private boolean makeMethods(ClassDescription[] similarClasses, ClassDescription result) {

        // methods
        ArrayList<MethodDescr> methods = new ArrayList<>();
        Set<String> h = new HashSet<>();

        for (int i = 0; i < similarClasses.length; i++) {
            MethodDescr[] mfs = similarClasses[i].getDeclaredMethods();
            for (MethodDescr mf : mfs) {
                ArrayList<MethodDescr> sameMethods = new ArrayList<>();
                ArrayList<Boolean> finalMods = new ArrayList<>();
                sameMethods.add(mf);
                finalMods.add(mf.isFinal() || similarClasses[i].isFinal());
                boolean isUnique = true;
                for (int k = 0; k < similarClasses.length; k++) {
                    if (k == i) {
                        continue;
                    }
                    MethodDescr[] mfs2 = similarClasses[k].getDeclaredMethods();
                    for (MethodDescr methodDescr : mfs2) {
                        if (methodDescr.getSignature().equals(mf.getSignature())) {
                            isUnique = false;
                            sameMethods.add(methodDescr);
                            finalMods.add(methodDescr.isFinal() || similarClasses[k].isFinal());
                        }
                    }
                }
                if (isUnique) {
                    if (!h.contains(mf.getSignature())) {
                        methods.add(mf);
                    }
                    h.add(mf.getSignature());
                } else {
                    // some merge
                    MethodDescr m = new MethodDescr();
                    if (mergeMethods(sameMethods.toArray(MethodDescr.EMPTY_ARRAY), m, finalMods)) {
                        if (!h.contains(m.getSignature())) {
                            methods.add(m);
                        }
                        h.add(m.getSignature());
                    } else {
                        // ??
                    }
                }
            }
        }

        result.setMethods(methods.toArray(MethodDescr.EMPTY_ARRAY));

        return true;
    }

    private boolean makeCtors(ClassDescription[] similarClasses, ClassDescription result) {
        ArrayList<ConstructorDescr> constr = new ArrayList<>();
        Set<String> h = new HashSet<>();
        for (int i = 0; i < similarClasses.length; i++) {
            ConstructorDescr[] cds = similarClasses[i].getDeclaredConstructors();
            for (ConstructorDescr cd : cds) {
                ArrayList<ConstructorDescr> sameConstr = new ArrayList<>();
                sameConstr.add(cd);
                boolean isUnique = true;
                for (int k = 0; k < similarClasses.length; k++) {
                    if (k == i) {
                        continue;
                    }
                    ConstructorDescr[] cds2 = similarClasses[k].getDeclaredConstructors();
                    for (ConstructorDescr constructorDescr : cds2) {
                        if (constructorDescr.getSignature().equals(cd.getSignature())) {
                            isUnique = false;
                            sameConstr.add(constructorDescr);
                        }
                    }
                }
                if (isUnique) {
                    if (!h.contains(cd.getSignature())) {
                        constr.add(cd);
                    }
                    h.add(cd.getSignature());
                } else {
                    // some merge
                    ConstructorDescr c = new ConstructorDescr();

                    if (mergeConstructors(sameConstr.toArray(ConstructorDescr.EMPTY_ARRAY), c)) {
                        if (!h.contains(c.getSignature())) {
                            constr.add(c);
                        }
                        h.add(c.getSignature());
                    } else {
                        // ??
                    }
                }
            }
        }

        result.setConstructors(constr.toArray(ConstructorDescr.EMPTY_ARRAY));
        return true;
    }

    private boolean mergeMethods(MemberDescription[] similarMethods, MemberDescription result, ArrayList<Boolean> finalMods) {
        String type = similarMethods[0].getType();
        for (int i = 1; i < similarMethods.length; i++) {
            if (!type.equals(similarMethods[i].getType())) {
                error(show(similarMethods[0]) + i18n.getString("Merger.error.typeconflict"), new Object[]{type, similarMethods[i].getType()});
                return false;
            }
        }

        if (!mergeMod(similarMethods, result)) {
            return false;
        }

        result.setupMemberName(similarMethods[0].getQualifiedName());
        result.setType(similarMethods[0].getType());
        mergeAnnotations(similarMethods, result);

        // if there is not abstract , clean abstract
        boolean notAbstract = false;
        boolean hasFinal = true;
        for (int i = 0; i < similarMethods.length; i++) {
            if (!similarMethods[i].isAbstract()) {
                notAbstract = true;
            }
            if (!finalMods.get(i)) {
                hasFinal = false;
            }
        }
        if (notAbstract) {
            result.setModifiers(result.getModifiers() & ~Modifier.ABSTRACT.getValue());
        }
        if (hasFinal) {
            result.setModifiers(result.getModifiers() | Modifier.FINAL.getValue());
        }

        result.setArgs(similarMethods[0].getArgs());
        result.setType(similarMethods[0].getType());

        mergeTypeParameters(similarMethods, result);

        if (!mergeThrows(similarMethods, result)) {
            return false;
        }

        return true;
    }

    private void mergeTypeParameters(MemberDescription[] similarMembers, MemberDescription result) {
        String tp = null;
        boolean unique = true;
        for (MemberDescription similarMember : similarMembers) {
            String tpp = similarMember.getTypeParameters();
            if (tpp != null) {
                if (tp == null || tpp.equals(tp)) {
                    tp = tpp;
                } else {
                    unique = false;
                }
            }
        }
        if (unique) {
            result.setTypeParameters(tp);
        } else {
            error(show(similarMembers[0]) + i18n.getString("Merger.error.typeconflict"), new Object[]{tp, similarMembers[0].getTypeParameters()});
        }
    }

    private static void mergeAnnotations(MemberDescription[] similarMembers, MemberDescription result) {
        TreeSet<AnnotationItem> annotations = new TreeSet<>();
        for (MemberDescription similarMember : similarMembers) {
            AnnotationItem[] annos = similarMember.getAnnoList();
            annotations.addAll(Arrays.asList(annos));
        }
        result.setAnnoList(annotations.toArray(AnnotationItem.EMPTY_ANNOTATIONITEM_ARRAY));

    }

    private boolean mergeConstructors(ConstructorDescr[] similarCtors, ConstructorDescr result) {
        String type = similarCtors[0].getType();
        for (int i = 1; i < similarCtors.length; i++) {
            if (!type.equals(similarCtors[i].getType())) {
                error(show(similarCtors[0]) + i18n.getString("Merger.error.typeconflict"), new Object[]{type, similarCtors[i].getType()});
                return false;
            }
        }

        if (!mergeMod(similarCtors, result)) {
            return false;
        }

        result.setupMemberName(similarCtors[0].getQualifiedName());
        result.setType(similarCtors[0].getType());
        result.setArgs(similarCtors[0].getArgs());
        mergeAnnotations(similarCtors, result);
        mergeTypeParameters(similarCtors, result);

        if (!mergeThrows(similarCtors, result)) {
            return false;
        }

        return true;
    }

    private boolean mergeThrows(MemberDescription[] similarMethods, MemberDescription result) {

        if (mo.isSet(Option.BINARY)) {
            result.setThrowables("");
            return true;
        } else {
            // merge normalized throw list
            // in this version we won't do it - the lists are already normalized
            String throwList = similarMethods[0].getThrowables();
            for (int i = 1; i < similarMethods.length; i++) {
                if (!throwList.equals(similarMethods[1].getThrowables())) {
                    Object[] tlist = {show(throwList), show(similarMethods[1].getThrowables())};
                    error(show(similarMethods[0]) + i18n.getString("Merger.error.throwconflict"), tlist);
                    return false;
                }
            }
            result.setThrowables(throwList);
        }

        return true;
    }

    private void error(String msg, Object[] params) {
        error(MessageFormat.format(msg, params));
    }

    private void error(String msg) {
        log.storeError(msg, null);
        result.error(i18n.getString("Merger.error"));
    }

    private static String show(MemberDescription x) {
        return x.getQualifiedName();
    }

    private static String show(String x) {
        return x;
    }

}
