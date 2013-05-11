/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2013 Jean-Noël Rouvignac - initial API and implementation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program under LICENSE-GNUGPL.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution under LICENSE-ECLIPSE, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.autorefactor.samples_in;

public class InvertEqualsSample {

    public static interface Itf {
        String constant = "fkjfkjf";
        String nullConstant = null;
    }

    public boolean invertEquals(Object obj) {
        return obj.equals("") && obj.equals(Itf.constant);
    }

    public boolean doNotInvertEquals(Object obj) {
        return obj.equals(Itf.nullConstant);
    }

    public boolean invertEqualsIgnoreCase(String s) {
        return s.equalsIgnoreCase("") && s.equalsIgnoreCase(Itf.constant);
    }

    public boolean doNotInvertEqualsIgnoreCase(String s) {
        return s.equalsIgnoreCase(Itf.nullConstant);
    }

    public static void main(String[] args) {
        {
            // Invert equals()
            boolean b1 = args[0] != null && "".equals(args[0]);
            boolean b2 = args[0] != null && Itf.constant.equals(args[0]);
            boolean b3 = null != args[0] && "".equals(args[0]);
            boolean b4 = null != args[0] && Itf.constant.equals(args[0]);
            // Do NOT invert equals()
            boolean b5 = args[0] != null && args[0].equals(Itf.nullConstant);
            boolean b6 = null != args[0] && args[0].equals(Itf.nullConstant);
        }
        {
            // Invert equals()
            boolean b1 = args[0] != null && "".equalsIgnoreCase(args[0]);
            boolean b2 = args[0] != null
                    && Itf.constant.equalsIgnoreCase(args[0]);
            boolean b3 = null != args[0] && "".equalsIgnoreCase(args[0]);
            boolean b4 = null != args[0]
                    && Itf.constant.equalsIgnoreCase(args[0]);
            // Do NOT invert equals()
            boolean b5 = args[0] != null
                    && args[0].equalsIgnoreCase(Itf.nullConstant);
            boolean b6 = null != args[0]
                    && args[0].equalsIgnoreCase(Itf.nullConstant);
        }
    }
}
