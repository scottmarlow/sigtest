/*
 * Copyright (c) 2006, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tdk.signaturetest.sigfile.f31;

import com.sun.tdk.signaturetest.model.*;
import com.sun.tdk.signaturetest.sigfile.AnnotationParser;
import com.sun.tdk.signaturetest.sigfile.Parser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Parse string representation used in sigfile v3.1 and create corresponding
 * member object
 *
 * @author Roman Makarchuk
 */
public class F31Parser implements Parser {

    private String line;
    private int linesz;
    private int idx;
    private char chr;
    private List<String> elems;
    private int directInterfaceCount;

    public ClassDescription parseClassDescription(String classDefinition, List<String> members) {

        directInterfaceCount = 0;

        ClassDescription classDescription = (ClassDescription) parse(classDefinition);
        MemberCollection classMembers = new MemberCollection();
        MemberDescription member = classDescription;
        List<String> alist = new ArrayList<>();

        for (String str : members) {
            if (str.startsWith(AnnotationItem.ANNOTATION_PREFIX)) {
                alist.add(str);
            } else {
                appendAnnotations(member, alist);
                member = parse(str);
                classMembers.addMember(member);
            }
        }

        appendAnnotations(member, alist);

        classDescription.setMembers(classMembers);

        if (directInterfaceCount > 0) {
            classDescription.createInterfaces(directInterfaceCount);
            int count = 0;
            for (Iterator<MemberDescription> it = classMembers.iterator(); it.hasNext(); ) {
                MemberDescription mr = it.next();
                if (mr.isSuperInterface()) {
                    SuperInterface si = (SuperInterface) mr;
                    if (si.isDirect()) {
                        classDescription.setInterface(count++, si);
                    }
                }
            }
        }

        ArrayList<MethodDescr> methods = new ArrayList<>();
        ArrayList<FieldDescr> fields = new ArrayList<>();
        ArrayList<ConstructorDescr> constrs = new ArrayList<>();
        ArrayList<InnerDescr> inners = new ArrayList<>();
        ArrayList<SuperInterface> intfs = new ArrayList<>();
        for (Iterator<MemberDescription> it = classMembers.iterator(); it.hasNext(); ) {
            MemberDescription md = it.next();
            if (md instanceof SuperClass) {
                classDescription.setSuperClass((SuperClass) md);
            } else if (md instanceof SuperInterface) {
                intfs.add((SuperInterface) md);
            }
            if (!md.getDeclaringClassName().equals(classDescription.getQualifiedName())) {
                continue;
            }
            if (md instanceof MethodDescr) {
                methods.add((MethodDescr) md);
            } else if (md instanceof ConstructorDescr) {
                constrs.add((ConstructorDescr) md);
            } else if (md instanceof FieldDescr) {
                fields.add((FieldDescr) md);
            } else if (md instanceof InnerDescr) {
                inners.add((InnerDescr) md);
            }
        }
        classDescription.setConstructors(constrs.toArray(ConstructorDescr.EMPTY_ARRAY));
        classDescription.setMethods(methods.toArray(MethodDescr.EMPTY_ARRAY));
        classDescription.setFields(fields.toArray(FieldDescr.EMPTY_ARRAY));
        classDescription.setNestedClasses(inners.toArray(InnerDescr.EMPTY_ARRAY));
        classDescription.setInterfaces(intfs.toArray(SuperInterface.EMPTY_ARRAY));
        return classDescription;
    }

    private static void appendAnnotations(MemberDescription fid, List<String> alist) {
        if (!alist.isEmpty()) {

            AnnotationItem[] tmp = new AnnotationItem[alist.size()];
            AnnotationParser par = new AnnotationParser();

            for (int i = 0; i < alist.size(); ++i) {
                tmp[i] = par.parse(alist.get(i));
            }

            fid.setAnnoList(tmp);
            alist.clear();
        }
    }

    private MemberDescription parse(String definition) {
        MemberDescription member = null;

        MemberType type = MemberType.getItemType(definition);

        if (type == MemberType.CLASS) {
            member = parse(new ClassDescription(), definition);
        } else if (type == MemberType.CONSTRUCTOR) {
            member = parse(new ConstructorDescr(), definition);
        } else if (type == MemberType.METHOD) {
            member = parse(new MethodDescr(), definition);
        } else if (type == MemberType.FIELD) {
            member = parse(new FieldDescr(), definition);
        } else if (type == MemberType.SUPERCLASS) {
            member = parse(new SuperClass(), definition);
        } else if (type == MemberType.SUPERINTERFACE) {
            member = parse(new SuperInterface(), definition);
            if (((SuperInterface) member).isDirect()) {
                ++directInterfaceCount;
            }
        } else if (type == MemberType.INNER) {
            member = parse(new InnerDescr(), definition);
        } else {
            assert false;  // unknown member type
        }

        return member;
    }

    private void init(MemberDescription m, String def) {
        //System.out.println(def);
        line = def.trim();
        linesz = line.length();

        // skip member type
        idx = def.indexOf(' ');

        scanElems();
        //System.out.println(elems);
    }

    protected MemberDescription parse(ClassDescription cls, String def) {

        init(cls, def);

        cls.setModifiers(Modifier.scanModifiers(elems));

        String s = getElem();
        cls.setupGenericClassName(s);

        return cls;
    }

    protected MemberDescription parse(ConstructorDescr ctor, String def) {

        init(ctor, def);

        ctor.setModifiers(Modifier.scanModifiers(elems));

        String s = getElem();
        if (s != null && s.charAt(0) == '<') {
            ctor.setTypeParameters(s);
            s = getElem();
        }

        ctor.setupConstuctorName(s);

        s = getElem();
        if (s.charAt(0) != '(') {
            err();
        }

        if (!"()".equals(s)) {
            ctor.setArgs(s.substring(1, s.length() - 1));
        }

        if (!elems.isEmpty()) {
            s = getElem();
            if (!s.equals("throws")) {
                err();
            }
            s = getElem();
            ctor.setThrowables(s);
        }

        return ctor;
    }

    protected MemberDescription parse(MethodDescr method, String def) {

        init(method, def);

        method.setModifiers(Modifier.scanModifiers(elems));

        String s = getElem();
        if (s != null && s.charAt(0) == '<') {
            method.setTypeParameters(s);
            s = getElem();
        }

        method.setType(s);

        method.setupMemberName(getElem());

        s = getElem();
        if (s.charAt(0) != '(') {
            err();
        }

        if (!"()".equals(s)) {
            method.setArgs(s.substring(1, s.length() - 1));
        }

        if (!elems.isEmpty()) {
            s = getElem();
            if (!s.equals("throws")) {
                err();
            }
            s = getElem();
            method.setThrowables(s);
        }

        return method;
    }

    protected MemberDescription parse(FieldDescr field, String def) {

        init(field, def);

        field.setModifiers(Modifier.scanModifiers(elems));

        String s = getElem();
        field.setType(s);

        s = getElem();

        field.setupMemberName(s);

        if (!elems.isEmpty()) {
            s = getElem();
            if (!s.startsWith("=")) {
                err();
            }

            field.setConstantValue(s.substring(1).trim());
        }

        return field;
    }

    protected MemberDescription parse(SuperClass superCls, String def) {

        init(superCls, def);

        int n = elems.size();
        if (n == 0) {
            err();
        }
        superCls.setupGenericClassName(elems.get(n - 1));

        return superCls;
    }

    protected MemberDescription parse(SuperInterface superIntf, String def) {

        init(superIntf, def);

        int n = elems.size();
        if (n == 0) {
            err();
        }

        if ("@".equals(elems.get(0))) {
            superIntf.setDirect(true);
        }

        superIntf.setupGenericClassName(elems.get(n - 1));
        return superIntf;
    }

    protected MemberDescription parse(InnerDescr inner, String def) {

        init(inner, def);

        inner.setModifiers(Modifier.scanModifiers(elems));

        String s = getElem();
        int i = s.lastIndexOf('$');
        if (i < 0) {
            err();
        }

        inner.setupClassName(s);

        return inner;
    }

    private String getElem() {
        String s = null;

        if (!elems.isEmpty()) {
            s = elems.get(0);
            elems.remove(0);
        }

        if (s == null) {
            err();
        }

        return s;
    }

    private void scanElems() {
        elems = new LinkedList<>();

        for (; ; ) {

            //  skip leading blanks at the start of lexeme
            while (idx < linesz && (chr = line.charAt(idx)) == ' ') {
                idx++;
            }

            //  test for end of line
            if (idx >= linesz) {
                break;
            }

            //  store the start position of lexeme
            int pos = idx;

            if (chr == '=') {
                idx = linesz;
                elems.add(line.substring(pos));
                break;
            }

            if (chr == '(') {
                idx++;
                skip(')');
                idx++;
                elems.add(line.substring(pos, idx));
                continue;
            }

            if (chr == '<') {
                idx++;
                skip('>');
                idx++;
                elems.add(line.substring(pos, idx));
                continue;
            }

            idx++;
            while (idx < linesz) {
                chr = line.charAt(idx);

                if (chr == '<') {
                    idx++;
                    skip('>');
                    idx++;
                    continue;
                }

                if (chr == ' ' || chr == '(') {
                    break;
                }

                idx++;
            }
            elems.add(line.substring(pos, idx));
        }
    }

    private void skip(char term) {
        for (; ; ) {
            if (idx >= linesz) {
                err();
            }

            if ((chr = line.charAt(idx)) == term) {
                return;
            }

            if (chr == '(') {
                idx++;
                skip(')');
                idx++;
                continue;
            }

            if (chr == '<') {
                idx++;
                skip('>');
                idx++;
                continue;
            }

            idx++;
        }
    }

    void err() {
        throw new Error(line);
    }
}
