/*
 * Copyright (c) 1999, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tdk.signaturetest.classpath;

import com.sun.tdk.signaturetest.model.ClassDescription;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Maxim Sokolnikov
 * @author Serguei Ivashin
 */
public abstract class ClasspathEntry implements Classpath {

    protected static final String JAVA_CLASSFILE_EXTENSION = ".class";
    protected static final int JAVA_CLASSFILE_EXTENSION_LEN = JAVA_CLASSFILE_EXTENSION.length();
    final protected ClasspathEntry previousEntry;

    protected ClasspathEntry(ClasspathEntry previousEntry) {
        this.previousEntry = previousEntry;
    }

    /**
     * Qualified names for all those classes found in {@code this}
     * directory.
     */
    protected Set<String> classes;
    /**
     * This {@code currentPosition} iterator is used to browse
     * {@code classes}
     */
    protected Iterator<String> currentPosition;

    public boolean hasNext() {
        return currentPosition.hasNext();
    }

    public String nextClassName() {
        return currentPosition.next();
    }

    /**
     * Reset enumeration of classes found in {@code this}
     * <b>ClasspathEntry</b>.
     *
     * @see #nextClassName()
     * @see #findClass(String)
     */
    public void setListToBegin() {
        currentPosition = classes.iterator();
    }

    protected boolean contains(String className) {
        return classes.contains(className) || (previousEntry != null && previousEntry.contains(className));
    }

    public boolean isEmpty() {
        return classes.isEmpty();
    }

    @Override
    public ClassDescription findClassDescription(String qualifiedClassName) throws ClassNotFoundException {
        throw new ClassNotFoundException(qualifiedClassName);
    }

    @Override
    public void printErrors(PrintWriter out) {
        throw new IllegalStateException();
    }

    @Override
    public KIND_CLASS_DATA isClassPresent(String qualifiedClassName) {
        throw new IllegalStateException();
    }
}
