/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2017 Fabrice Tiercelin - Separate the code.
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
package org.autorefactor.jdt.internal.ui.fix;

import org.autorefactor.jdt.internal.corext.dom.ASTNodeFactory;
import org.autorefactor.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;

/** See {@link #getDescription()} method. */
public class BooleanConstantRatherThanValueOfCleanUp extends AbstractCleanUpRule {
    /**
     * Get the name.
     *
     * @return the name.
     */
    public String getName() {
        return MultiFixMessages.CleanUpRefactoringWizard_BooleanConstantRatherThanValueOfCleanUp_name;
    }

    /**
     * Get the description.
     *
     * @return the description.
     */
    public String getDescription() {
        return MultiFixMessages.CleanUpRefactoringWizard_BooleanConstantRatherThanValueOfCleanUp_description;
    }

    /**
     * Get the reason.
     *
     * @return the reason.
     */
    public String getReason() {
        return MultiFixMessages.CleanUpRefactoringWizard_BooleanConstantRatherThanValueOfCleanUp_reason;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        if (ASTNodes.usesGivenSignature(node, Boolean.class.getCanonicalName(), "valueOf", String.class.getCanonicalName()) //$NON-NLS-1$
                || ASTNodes.usesGivenSignature(node, Boolean.class.getCanonicalName(), "valueOf", boolean.class.getSimpleName())) { //$NON-NLS-1$
            final BooleanLiteral literal= ASTNodes.as(ASTNodes.arguments(node), BooleanLiteral.class);

            if (literal != null) {
                useConstant(node, literal);
                return false;
            }
        }
        return true;
    }

    private void useConstant(MethodInvocation node, final BooleanLiteral literal) {
        final ASTNodeFactory b= ctx.getASTBuilder();
        final FieldAccess fa= b.getAST().newFieldAccess();
        final Name expression= ASTNodes.as(node.getExpression(), Name.class);

        if (expression != null) {
            fa.setExpression(b.copy(expression));
        }

        fa.setName(b.simpleName(literal.booleanValue() ? "TRUE" : "FALSE")); //$NON-NLS-1$ $NON-NLS-2$
        ctx.getRefactorings().replace(node, fa);
    }
}
