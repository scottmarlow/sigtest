/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tdk.signaturetest.model;

import com.sun.tdk.signaturetest.core.PrimitiveTypes;
import com.sun.tdk.signaturetest.util.SwissKnife;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * <b>MemberDescription</b> describes a class member, such as field, or method,
 * or constructor, or nested class or interface. It contains all modifiers, type
 * for field or returned type for method, name, types of method's or
 * constructor's arguments, and declared exceptions. It does not keep any
 * ``<I><b>extends</b></I> ...'' nor ``<I><b>implements</b></I> ...''
 * information for nested class.
 *
 * @author Maxim Sokolnikov
 */
public abstract class MemberDescription implements Cloneable, Serializable {

    public static final String EMPTY_THROW_LIST = "";
    public static final String NO_ARGS = "";
    public static final String NO_TYPE = "";
    public static final String NO_DECLARING_CLASS = "";
    // String is used only for convenience. it must be 1 char length.
    public static final String THROWS_DELIMITER = ",";
    public static final String ARGS_DELIMITER = ",";
    public static final String JAVA_LANG = "java.lang";
    public static final char CLASS_DELIMITER = '$';
    public static final char MEMBER_DELIMITER = '.';

    protected MemberDescription(MemberType memberType, char delimiter) {
        this.memberType = memberType;
        this.delimiter = delimiter;
    }

    protected final char delimiter;
    /**
     * All modifiers assigned to {@code this} item.
     *
     * @see com.sun.tdk.signaturetest.model.Modifier Direct access to this field
     * not allowed because method setModifiers(int) changes incoming modifiers!
     * @see #setModifiers(int)
     */
    private int modifiers = 0;
    //  For classes, methods and constructors: generic type parameters or null
    String typeParameters;
    /**
     * FieldDescr type, if {@code this} item describes some field, or
     * return type, if {@code this} item describes some methods. null value
     * not allowed!
     */
    String type = NO_TYPE;
    /**
     * Qualified name of the class or interface, where {@code this} item is
     * declared. null value not allowed!
     */
    String declaringClass = NO_DECLARING_CLASS;
    /**
     * If {@code this} item describes some method or constructor,
     * {@code args} lists types of its arguments. Type names are separated
     * by commas, and the whole {@code args} list is embraced inside
     * matching parentheses. in form: (arg,arg,...) null value not allowed!
     */
    String args = NO_ARGS;
    /**
     * Contains <I><b>throws</b></I> clause, if {@code this} item describes
     * some method or constructor. in form: throws t,t,...t null value not
     * allowed!
     */
    String throwables = EMPTY_THROW_LIST;
    //  Sorted list of annotations present on this item or null
    private AnnotationItem[] annoList = AnnotationItem.EMPTY_ANNOTATIONITEM_ARRAY;
    /**
     * Sort of entity referred by {@code this} item. It should be either
     * field, or method, or constructor, or class or inner class, or interface
     * being implemented by some class, or <I>superclass</I> being extended by
     * some class.
     *
     * @see #isField()
     * @see #isMethod()
     * @see #isConstructor()
     * @see #isClass()
     * @see #isSuperClass()
     * @see #isSuperInterface()
     * @see #isPermittedSubClass() ()
     * @see #isInner()
     */
    MemberType memberType;
    //  For classes, superclasses and superinterfaces: fully-qualified class name
    //  For other members: short name including inners
    // all names are interned. this helps to save memory, specially in binary mode!
    // Note! since all names interned it's possible to use == instead of equals()
    String name = "";

    // using this method is a bad practice!
    public final Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            SwissKnife.reportThrowable(e);
        }
        return null;
    }

    // this method must have package access !!!
    public MemberType getMemberType() {
        return memberType;
    }

    public String getName() {
        return name;
    }

    public int hashCode() {
        int hash = 5;
        hash = 23 * hash + (type != null ? type.hashCode() : 0);
        hash = 23 * hash + (declaringClass != null ? declaringClass.hashCode() : 0);
        hash = 23 * hash + (memberType != null ? memberType.hashCode() : 0);
        hash = 23 * hash + (name != null ? name.hashCode() : 0);
        return hash;
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MemberDescription other = (MemberDescription) obj;
        if (!this.type.equals(other.type)
                || !this.declaringClass.equals(other.declaringClass)
                || !this.memberType.equals(other.memberType)
                || !this.name.equals(other.name)) {
            return false;
        }
        return true;
    }

    /**
     * Check if {@code this} is a class item.
     */
    public boolean isClass() {
        return false;
    }

    /**
     * Check if {@code this} item describes superclass for some class.
     * (I.e., check if {@code this} describes ``<I><b>extends</b></I> ...''
     * suffix for some
     * <b>ClassDescription</b>.)
     */
    public boolean isSuperClass() {
        return false;
    }

    /**
     * Check if {@code this} item describes interface class for some class.
     * (I.e., check if {@code this} describes some of interface name(s) in
     * ``<I><b>implements</b></I> ...'' suffix for some
     * <b>ClassDescription</b>.)
     */
    public boolean isSuperInterface() {
        return false;
    }

    /**
     * Check if {@code this} item describes permitted subclass for some sealed class.
     */
    public boolean isPermittedSubClass() {
        return false;
    }

    /**
     * Check if {@code this} item describes some field.
     */
    public boolean isField() {
        return false;
    }

    /**
     * Check if {@code this} item describes some method.
     */
    public boolean isMethod() {
        return false;
    }

    /**
     * Check if {@code this} item describes some constructor.
     */
    public boolean isConstructor() {
        return false;
    }

    public boolean isInner() {
        return false;
    }

    /**
     * Return <b>Set</b> of Modifier assigned to {@code this} item.
     *
     * @see Modifier
     */
    public int getModifiers() {
        return modifiers;
    }

    /**
     * Display return-type if {@code this} describes some method, or type
     * of the field if {@code this} describes some field.
     */
    public String getType() {
        return type;
    }

    public String getTypeParameters() {
        return typeParameters;
    }

    // SuperInterface, SuperClass, ClassDescription,
    public void setupGenericClassName(String superClassName, String outer) {

        int pos = superClassName.indexOf('<');
        String tmp = superClassName;
        if (pos != -1) {
            tmp = superClassName.substring(0, pos);
            typeParameters = superClassName.substring(pos);
        } else {
            typeParameters = null;
        }

        setupClassName(tmp, outer);
    }

    // old-style version for old formats
    public void setupGenericClassName(String superClassName) {

        int pos = superClassName.indexOf('<');
        String tmp = superClassName;
        if (pos != -1) {
            tmp = superClassName.substring(0, pos);
            typeParameters = superClassName.substring(pos);
        } else {
            typeParameters = null;
        }

        setupClassName(tmp);
    }

    public void setupClassName(String fqn, String outerName) {

        fqn = ExoticCharTools.encodeExotic(fqn);
        outerName = ExoticCharTools.encodeExotic(outerName);

        if (memberType == MemberType.CLASS
                || memberType == MemberType.SUPERCLASS
                || memberType == MemberType.SUPERINTERFACE
                || memberType == MemberType.PERMITTEDSUBCLASS) {

            name = fqn.intern();

        } else {

            if (!outerName.equals(NO_DECLARING_CLASS)
                    && fqn.startsWith(outerName)
                    && !outerName.equals(fqn)) {
                name = fqn.substring(outerName.length());
                if (name.charAt(0) == delimiter) {
                    name = name.substring(1);
                }
            } else {
                name = fqn;
            }
            name = name.intern();
        }

        if (!outerName.equals(NO_DECLARING_CLASS) && !outerName.equals(fqn)) {
            declaringClass = outerName.intern();
        } else {
            declaringClass = NO_DECLARING_CLASS;
        }

    }

    // ClassDescription, InnerDescr and all members.
    // old-style version for old formats
    public void setupClassName(String fqn) {

        fqn = ExoticCharTools.encodeExotic(fqn);

        int delimPos = fqn.lastIndexOf(delimiter);

        if (memberType == MemberType.CLASS
                || memberType == MemberType.SUPERCLASS
                || memberType == MemberType.SUPERINTERFACE
                || memberType == MemberType.PERMITTEDSUBCLASS) {

            name = fqn.intern();

            if (delimPos != -1) {
                declaringClass = fqn.substring(0, delimPos).intern();
            } else {
                declaringClass = NO_DECLARING_CLASS;
            }

        } else {

            if (delimPos >= 0) // this is possible if a inner class was obfuscated and has no dollar sign
            {
                declaringClass = fqn.substring(0, delimPos).intern();
            }
            name = fqn.substring(delimPos + 1).intern();
        }
    }

    // only inner in F40Parser
    public void setupInnerClassName(String name, String declaringClassName) {
        declaringClass = declaringClassName.intern();
        this.name = name.intern();
    }

    // only field and method
    public void setupMemberName(String own, String dcl) {
        declaringClass = dcl.intern();
        own = ExoticCharTools.encodeExotic(own);
        name = own.intern();
    }

    // only field, method and constructor
    public void setupMemberName(String fqn) {
        int pos = fqn.lastIndexOf(delimiter);

        declaringClass = fqn.substring(0, pos).intern();
        name = fqn.substring(pos + 1).intern();
    }

    /**
     * Display qualified name of the class or interface declaring
     * {@code this} item. Empty string is returned if {@code this}
     * item describes top-level class or interface, which is not inner class or
     * interface.
     */
    public String getDeclaringClassName() {
        return declaringClass;
    }

    public AnnotationItem[] getAnnoList() {
        return annoList;
    }

    // default implementation.
    // For ClassDescription, SuperClass, SuperInteraface this method must be overriden!
    public String getQualifiedName() {
        return declaringClass + delimiter + name;
    }

    /**
     * Returns list of exception names separated by commas declared in the
     * <I><b>throws</b></I> clause for that method or constructor described by
     * {@code this} item.
     */
    public String getThrowables() {
        return throwables;
    }

    private boolean marked = false;

    public void mark() {
        marked = true;
    }

    public void unmark() {
        marked = false;
    }

    public boolean isMarked() {
        return marked;
    }

    /**
     * Check if modifiers list for {@code this} item contains the
     * {@code "protected"} string.
     */
    public boolean isProtected() {
        return Modifier.hasModifier(modifiers, Modifier.PROTECTED);
    }

    /**
     * Check if modifiers list for {@code this} item contains the
     * {@code "public"} string.
     */
    public boolean isPublic() {
        return Modifier.hasModifier(modifiers, Modifier.PUBLIC);
    }

    /**
     * Check if modifiers list for {@code this} item contains the
     * {@code "private"} string.
     */
    public boolean isPrivate() {
        return Modifier.hasModifier(modifiers, Modifier.PRIVATE);
    }

    /**
     * Check if modifiers list for {@code this} item contains the
     * {@code "abstract"} string.
     */
    public boolean isAbstract() {
        return Modifier.hasModifier(modifiers, Modifier.ABSTRACT);
    }

    /**
     * Check if modifiers list for {@code this} item contains the
     * {@code "static"} string.
     */
    public boolean isStatic() {
        return Modifier.hasModifier(modifiers, Modifier.STATIC);
    }

    public boolean isFinal() {
        return Modifier.hasModifier(modifiers, Modifier.FINAL);
    }

    /**
     * Check if modifiers list for {@code this} item contains the
     * {@code "interface"} string.
     */
    public boolean isInterface() {
        return Modifier.hasModifier(modifiers, Modifier.INTERFACE);
    }

    //  Convert constant value to string representation
    //  used in sigfile.
    //
    public static String valueToString(Object value) {

        if (value == null) {
            return "";
        }

        if (value.getClass().isArray()) {
            StringBuffer sb = new StringBuffer();
            sb.append('[');
            int n = Array.getLength(value);
            for (int i = 0; i < n; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(valueToString(Array.get(value, i)));
            }
            sb.append(']');
            return sb.toString();
        } else if (value instanceof Character) {
            return "\'" + stuffOut(value.toString()) + "\'";
        } else if (value instanceof String) {
            return "\"" + stuffOut(value.toString()) + "\"";
        } else if (value instanceof Long) {
            return value.toString(); // + "L";
        } else if (value instanceof Float) {
            Float f = (Float) value;
            return f.toString();
//            if (!f.isNaN() && !f.isInfinite())
//                s += "f";
        } else if (value instanceof Double) {
            Double d = (Double) value;
            return d.toString();
//            if (!d.isNaN() && !d.isInfinite())
//                s += "d";
        } else // boolean, byte, short, int
        {
            return value.toString();
        }
    }

    private static String stuffOut(String s) {
        StringBuffer x = new StringBuffer();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if ((0x20 <= c && c <= 0x7E) && c != '\"' && c != '\\') {
                x.append(c);
            } else if (c == '\r') {
                x.append("\\r");
            } else if (c == '\n') {
                x.append("\\n");
            } else {
                x.append(esc(c));
            }
        }

        return x.toString();
    }

    private static String esc(char c) {
        String s = Integer.toHexString(c);
        int n = s.length();

        if (n == 1) {
            return "\\u000" + s;
        } else if (n == 2) {
            return "\\u00" + s;
        } else if (n == 3) {
            return "\\u0" + s;
        } else {
            return "\\u" + s;
        }
    }

    public void setModifiers(int access) {

        int mask = memberType.getModifiersMask();

        if ((access & mask) != access) {
            throw new ClassFormatError("Unknown modifier(s) found " + (access & ~mask));
        }

        modifiers = access;

        if (Modifier.hasModifier(modifiers, Modifier.INTERFACE)) {
            modifiers = Modifier.addModifier(modifiers, Modifier.ABSTRACT);
        }

        // ===== end of workaround =====
    }

    public void addModifier(Modifier mod) {
        modifiers = Modifier.addModifier(modifiers, mod);
    }

    public void removeModifier(Modifier mod) {
        modifiers = Modifier.removeModifier(modifiers, mod);
    }

    public static String getTypeName(Class<?> c) {
        String className = c.getName();

        if (!className.startsWith("[")) {
            return className;
        }

        return getTypeName(className);
    }

    public static String getTypeName(String className) {

        StringBuffer sb = new StringBuffer();

        int dims = 0;
        while (className.charAt(dims) == '[') {
            dims++;
        }

        String type;

        if (className.charAt(dims) == 'L') {
            type = className.substring(dims + 1, className.length() - 1);
        } else {
            type = PrimitiveTypes.getPrimitiveType(className.charAt(dims));
        }

        sb.append(type);
        for (int i = 0; i < dims; ++i) {
            sb.append("[]");
        }

        return sb.toString();
    }

    public String getArgs() {
        return args;
    }

    /**
     * Converts qualified member type to simplified 1. Classes from package
     * java.lang referenced by their simple name 2. Classes from the same
     * package referenced by their simple name 3. The others are qualified.
     *
     * @return simplified type as String
     */
    public String getSimplifiedType() {
        return simplifyType(type);
    }

    /**
     * Converts qualified list of member arguments to simplified 1. Classes from
     * package java.lang referenced by their simple name 2. Classes from the
     * same package referenced by their simple name 3. The others are qualified.
     *
     * @return simplified arguments as String
     */
    public String getSimplifiedArgs() {
        if (args == null || args.isEmpty()) {
            return args;
        } else {
            StringBuffer sb = new StringBuffer();
            StringTokenizer st = new StringTokenizer(args, ARGS_DELIMITER);
            while (st.hasMoreTokens()) {
                sb.append(simplifyType(st.nextToken().trim()));
                if (st.hasMoreTokens()) {
                    sb.append(ARGS_DELIMITER);
                }
            }
            return sb.toString();
        }
    }

    private String simplifyType(String type) {
        String pack = getPackageName(type);
        String thisPack = getPackageName(getDeclaringClassName());
        if (pack.equals(JAVA_LANG) || pack.equals(thisPack)) {
            return getClassShortName(type);
        }
        return type;
    }

    public boolean setType(String type) {
        if (this.type.equals(type)) {
            return false;
        }

        this.type = type.intern();
        return true;
    }

    public boolean setArgs(String args) {
        if (this.args.equals(args)) {
            return false;
        }

        // this is just memory usage optimization
        if (!args.contains(ARGS_DELIMITER)) {
            this.args = args.intern();
        } else {
            this.args = args;
        }

        return true;
    }

    public boolean setThrowables(String throwables) {
        if (this.throwables.equals(throwables)) {
            return false;
        }

        // this is just memory usage optimization
        if (!throwables.contains(THROWS_DELIMITER)) {
            this.throwables = throwables.intern();
        } else {
            this.throwables = throwables;
        }

        return true;
    }

    public void setAnnoList(AnnotationItem[] annoList) {
        this.annoList = annoList;
        // in fact, Arrays.sort() is slow, because clone the whole array passed as input parameter
        if (annoList.length > 1) {
            Arrays.sort(this.annoList);
        }
    }

    public void setTypeParameters(String typeParameters) {
        this.typeParameters = typeParameters;
    }

    // should be used for members only
    public void setDeclaringClass(String declaringClass) {
        if (declaringClass == null || NO_DECLARING_CLASS.equals(declaringClass)) {
            throw new IllegalArgumentException();
        }

        this.declaringClass = declaringClass.intern();
    }

    public void setNoDeclaringClass() {
        this.declaringClass = NO_DECLARING_CLASS;
    }

    //  Pack exception lists in the following way:
    //      throws <e1>, ... <eN>
    //  one blank follows 'throw' keyword, exceptions are separated by ',' without blanks.
    //  If the exception list is empty, empty string is returned.
    //  Exceptions are sorted alphabetically
    //
    //  Note: this method has side-effect - its parameter (xthrows) gets sorted.
    //
    public static String getThrows(String[] xthrows) {
        if (xthrows == null || xthrows.length == 0) {
            return EMPTY_THROW_LIST;
        }

        // in fact, Arrays.sort() is slow, because clone the whole array passed as input parameter
        if (xthrows.length > 1) {
            Arrays.sort(xthrows);
        }

        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < xthrows.length; i++) {
            if (i != 0) {
                sb.append(THROWS_DELIMITER);
            }
            sb.append(xthrows[i]);
        }

        return sb.toString();
    }

    public boolean hasModifier(Modifier mod) {
        return Modifier.hasModifier(modifiers, mod);
    }

    public abstract boolean isCompatible(MemberDescription m);

    protected void populateDependences(Set<String> dependences) {
    }

    protected void addDependency(Set<String> dependences, String newDependency) {

        if (newDependency.charAt(0) == '{') {
            return;
        }

        String temp = newDependency;

        int pos = temp.indexOf('<');
        if (pos != -1) {
            temp = temp.substring(0, pos);
        }

        pos = temp.indexOf('[');
        if (pos != -1) {
            temp = temp.substring(0, pos);
        }

        if (PrimitiveTypes.isPrimitive(newDependency)) {
            return;
        }

        dependences.add(temp);
    }

    public static String getClassShortName(String fqn) {
        String result = fqn;
        int pos = Math.max(fqn.lastIndexOf(MEMBER_DELIMITER), fqn.lastIndexOf(CLASS_DELIMITER));
        if (pos != -1) {
            result = fqn.substring(pos + 1);
        }
        return result;
    }

    protected static String getPackageName(String fqn) {
        String result = fqn;
        int pos = fqn.lastIndexOf(MEMBER_DELIMITER);
        if (pos != -1) {
            result = fqn.substring(0, pos);
        }
        return result;
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        memberType = MemberType.getItemType(memberType.toString());

        // intern fields after deserialization
        setupMemberName(name, declaringClass);
        setArgs(args);
        setThrowables(throwables);
    }
}
