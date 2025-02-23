/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2019 Fabrice Tiercelin - initial API and implementation
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

import java.util.List;
import java.util.Set;

import org.autorefactor.jdt.internal.corext.dom.ASTNodeFactory;
import org.autorefactor.jdt.internal.corext.dom.ASTNodes;
import org.autorefactor.jdt.internal.corext.dom.Refactorings;
import org.autorefactor.jdt.internal.corext.dom.VarOccurrenceVisitor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

/** See {@link #getDescription()} method. */
public class ReduceIndentationCleanUp extends AbstractCleanUpRule {
    private static final class IndentationVisitor extends ASTVisitor {
        private int indentation= 0;

        public int getIndentation() {
            return indentation;
        }

        @Override
        public boolean visit(IfStatement node) {
            computeGreatestIndentation(node.getThenStatement());

            if (node.getElseStatement() != null) {
                computeGreatestIndentation(node.getElseStatement());
            }
            return false;
        }

        @Override
        public boolean visit(WhileStatement node) {
            computeGreatestIndentation(node.getBody());
            return false;
        }

        @Override
        public boolean visit(DoStatement node) {
            computeGreatestIndentation(node.getBody());
            return false;
        }

        @Override
        public boolean visit(ForStatement node) {
            computeGreatestIndentation(node.getBody());
            return false;
        }

        @Override
        public boolean visit(EnhancedForStatement node) {
            computeGreatestIndentation(node.getBody());
            return false;
        }

        @Override
        public boolean visit(TryStatement node) {
            computeGreatestIndentation(node.getBody());

            for (Object object : node.catchClauses()) {
                final CatchClause clause= (CatchClause) object;
                computeGreatestIndentation(clause.getBody());
            }

            if (node.getFinally() != null) {
                computeGreatestIndentation(node.getFinally());
            }

            if (node.getFinally() != null) {
                computeGreatestIndentation(node.getFinally());
            }
            return false;
        }

        @Override
        public boolean visit(Block node) {
            computeGreatestIndentation(node);
            return false;
        }

        private void computeGreatestIndentation(final Statement statements) {
            for (Statement statement : ASTNodes.asList(statements)) {
                IndentationVisitor visitor= new IndentationVisitor();

                statement.accept(visitor);

                indentation= Math.max(indentation, visitor.getIndentation() + 1);
            }
        }
    }

    /**
     * Get the name.
     *
     * @return the name.
     */
    public String getName() {
        return MultiFixMessages.CleanUpRefactoringWizard_ReduceIndentationCleanUp_name;
    }

    /**
     * Get the description.
     *
     * @return the description.
     */
    public String getDescription() {
        return MultiFixMessages.CleanUpRefactoringWizard_ReduceIndentationCleanUp_description;
    }

    /**
     * Get the reason.
     *
     * @return the reason.
     */
    public String getReason() {
        return MultiFixMessages.CleanUpRefactoringWizard_ReduceIndentationCleanUp_reason;
    }

    @Override
    public boolean visit(IfStatement node) {
        if (node.getElseStatement() != null && ASTNodes.canHaveSiblings(node)) {
            if (ASTNodes.fallsThrough(node.getThenStatement())) {
                if (ASTNodes.fallsThrough(node.getElseStatement())) {
                    if (ASTNodes.getNextSiblings(node).isEmpty()) {
                        IndentationVisitor visitor= new IndentationVisitor();
                        node.getThenStatement().accept(visitor);
                        int thenIndentation= visitor.getIndentation();

                        visitor= new IndentationVisitor();
                        node.getThenStatement().accept(visitor);
                        int elseIndentation= visitor.getIndentation();

                        if (thenIndentation <= elseIndentation || node.getElseStatement() instanceof IfStatement) {
                            moveElseStatement(node);
                        } else {
                            moveThenStatement(node);
                        }

                        return false;
                    }
                } else if (!hasVariableConflict(node, node.getElseStatement())) {
                    moveElseStatement(node);
                    return false;
                }
            } else if (ASTNodes.fallsThrough(node.getElseStatement()) && !hasVariableConflict(node, node.getThenStatement()) && !(node.getElseStatement() instanceof IfStatement)) {
                moveThenStatement(node);
                return false;
            }
        }

        return true;
    }

    private void moveThenStatement(IfStatement node) {
        final Refactorings r= this.ctx.getRefactorings();
        final ASTNodeFactory b= this.ctx.getASTBuilder();

        final List<Statement> statements= ASTNodes.asList(node.getThenStatement());

        for (int i= statements.size() - 1; i >= 0; i--) {
            r.insertAfter(b.move(statements.get(i)), node);
        }

        r.replace(node.getExpression(), b.negate(node.getExpression()));
        r.replace(node.getThenStatement(), b.move(node.getElseStatement()));
        r.remove(node.getElseStatement());
    }

    private void moveElseStatement(IfStatement node) {
        final Refactorings r= this.ctx.getRefactorings();
        final ASTNodeFactory b= this.ctx.getASTBuilder();

        final List<Statement> statements= ASTNodes.asList(node.getElseStatement());

        for (int i= statements.size() - 1; i >= 0; i--) {
            r.insertAfter(b.move(statements.get(i)), node);
        }

        r.remove(node.getElseStatement());
    }

    private boolean hasVariableConflict(IfStatement node, final Statement statementInBlock) {
        final Set<String> ifVariableNames= ASTNodes.getLocalVariableIdentifiers(statementInBlock, false);

        for (Statement statement : ASTNodes.getNextSiblings(node)) {
            final VarOccurrenceVisitor varOccurrenceVisitor= new VarOccurrenceVisitor(ifVariableNames);
            varOccurrenceVisitor.visitNode(statement);

            if (varOccurrenceVisitor.isVarUsed()) {
                return true;
            }
        }

        return false;
    }
}
