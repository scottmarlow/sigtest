/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This class represents permitted sublass for sealed type
 *
 * @author Victor Rudometov
 */
public final class PermittedSubClass extends MemberDescription {
    public static final PermittedSubClass[] EMPTY_ARRAY = new PermittedSubClass[0];

    public PermittedSubClass() {
        super(MemberType.PERMITTEDSUBCLASS, CLASS_DELIMITER);
    }

    // NOTE: Change this method carefully if you changed the code,
    // please, update the method isCompatible() in order it works as previously
    public boolean equals(Object o) {
        // == used instead of equals() because name is always assigned via String.intern() call
        return o instanceof PermittedSubClass && name == ((PermittedSubClass) o).name;
    }

    public int hashCode() {
        return name.hashCode();
    }

    public String getQualifiedName() {
        return name;
    }

    public String getName() {
        return getClassShortName(name);
    }

    public boolean isCompatible(MemberDescription m) {
        if (!equals(m)) {
            throw new IllegalArgumentException("Only equal members can be checked for compatibility!");
        }

        return memberType.isCompatible(getModifiers(), m.getModifiers());
    }

    public boolean isPermittedSubClass() {
        return true;
    }

    public String toString() {
        return "permittedsubclass " + name;
    }
}
