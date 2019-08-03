/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2015-2018 Jean-Noël Rouvignac - initial API and implementation
 * Copyright (C) 2019 Fabrice TIERCELIN - Reuse for Collection.containsAll()
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

import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.as;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.asExpression;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.asList;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.getNextSibling;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.getNextStatement;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.getPreviousSibling;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.getPreviousStatement;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.getUniqueFragment;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.hasOperator;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.instanceOf;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.isSameLocalVariable;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.isSameVariable;
import static org.autorefactor.jdt.internal.corext.dom.ASTNodes.match;
import static org.autorefactor.jdt.internal.corext.dom.ForLoopHelper.decomposeInitializer;
import static org.autorefactor.jdt.internal.corext.dom.ForLoopHelper.iterateOverContainer;
import static org.autorefactor.jdt.internal.corext.dom.ForLoopHelper.ContainerType.COLLECTION;
import static org.autorefactor.jdt.internal.corext.dom.ForLoopHelper.IterationType.INDEX;
import static org.autorefactor.jdt.internal.corext.dom.ForLoopHelper.IterationType.ITERATOR;
import static org.eclipse.jdt.core.dom.Assignment.Operator.ASSIGN;

import java.util.List;

import org.autorefactor.jdt.internal.corext.dom.ASTBuilder;
import org.autorefactor.jdt.internal.corext.dom.BlockSubVisitor;
import org.autorefactor.jdt.internal.corext.dom.FinderVisitor;
import org.autorefactor.jdt.internal.corext.dom.ForLoopHelper.ForLoopContent;
import org.autorefactor.util.NotImplementedException;
import org.autorefactor.util.Pair;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/** See {@link #getDescription()} method. */
public abstract class AbstractCollectionMethodRatherThanLoopCleanUp extends AbstractCleanUpRule {
    /**
     * Get the expression to find.
     *
     * @param condition The condition
     * @param forVar    The variable
     * @return The expression
     */
    protected abstract Expression getExpressionToFind(MethodInvocation condition, Expression forVar);

    /**
     * Returns the method to replace or null otherwise.
     *
     * @param condition The condition
     * @return true if it is method to replace.
     */
    protected abstract MethodInvocation getMethodToReplace(Expression condition);

    /**
     * Generate the future method.
     *
     * @param iterable   The iterable
     * @param toFind     The expression to find
     * @param isPositive true if the expression is positive
     * @param b          The builder
     * @return the future method.
     */
    protected abstract Expression newMethod(Expression iterable, Expression toFind, boolean isPositive, ASTBuilder b);

    @Override
    public boolean visit(Block node) {
        final AssignmentForAndReturnVisitor assignmentForAndReturnVisitor= new AssignmentForAndReturnVisitor(ctx, node);
        node.accept(assignmentForAndReturnVisitor);
        return assignmentForAndReturnVisitor.getResult();
    }

    private final class AssignmentForAndReturnVisitor extends BlockSubVisitor {
        public AssignmentForAndReturnVisitor(final RefactoringContext ctx, final Block startNode) {
            super(ctx, startNode);
        }

        @Override
        public boolean visit(EnhancedForStatement node) {
            final SingleVariableDeclaration loopVariable= node.getParameter();
            final IfStatement is= uniqueStmtAs(node.getBody(), IfStatement.class);
            return maybeReplaceWithCollectionContains(node, node.getExpression(), loopVariable.getName(), is);
        }

        private boolean maybeReplaceWithCollectionContains(Statement forNode, Expression iterable,
                Expression loopElement, IfStatement is) {
            if (is != null && is.getElseStatement() == null && instanceOf(iterable, "java.util.Collection")) {
                MethodInvocation cond= getMethodToReplace(is.getExpression());
                List<Statement> thenStmts= asList(is.getThenStatement());

                if (!thenStmts.isEmpty() && cond != null) {
                    Expression toFind= getExpressionToFind(cond, loopElement);

                    if (toFind != null) {
                        if (thenStmts.size() == 1) {
                            Statement thenStmt= thenStmts.get(0);
                            BooleanLiteral innerBl= getReturnedBooleanLiteral(thenStmt);

                            Statement forNextStmt= getNextStatement(forNode);
                            BooleanLiteral outerBl= getReturnedBooleanLiteral(forNextStmt);

                            Boolean isPositive= signCollectionContains(innerBl, outerBl);

                            if (isPositive != null) {
                                replaceLoopAndReturn(forNode, iterable, toFind, forNextStmt, isPositive);
                                setResult(false);
                                return false;
                            }

                            return maybeReplaceLoopAndVariable(forNode, iterable, thenStmt, toFind);
                        }

                        BreakStatement bs= as(thenStmts.get(thenStmts.size() - 1), BreakStatement.class);

                        if (bs != null && bs.getLabel() == null) {
                            if (thenStmts.size() == 2 && !maybeReplaceLoopAndVariable(forNode, iterable,
                                    thenStmts.get(0), toFind)) {
                                return false;
                            }

                            if (loopElementIsUsed(loopElement, thenStmts)) {
                                // Cannot remove the loop and its loop element
                                return true;
                            }

                            replaceLoopByIf(forNode, iterable, thenStmts, toFind, bs);
                            setResult(false);
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private boolean loopElementIsUsed(Expression loopElement, List<Statement> thenStmts) {
            if (loopElement instanceof SimpleName) {
                VarUseFinderVisitor visitor= new VarUseFinderVisitor((SimpleName) loopElement);

                for (Statement aThenStmt : thenStmts) {
                    if (visitor.findOrDefault(aThenStmt, false)) {
                        return true;
                    }
                }
            }

            return false;
        }

        private class VarUseFinderVisitor extends FinderVisitor<Boolean> {
            private final SimpleName varName;

            public VarUseFinderVisitor(SimpleName varName) {
                this.varName= varName;
            }

            @Override
            public boolean visit(SimpleName variable) {
                if (isSameLocalVariable(varName, variable)) {
                    setResult(true);
                    return false;
                }

                return true;
            }
        }

        private void replaceLoopByIf(Statement forNode, Expression iterable, List<Statement> thenStmts,
                Expression toFind, BreakStatement bs) {
            thenStmts.remove(thenStmts.size() - 1);

            ASTBuilder b= ctx.getASTBuilder();
            Statement replacement= b.if0(newMethod(iterable, toFind, true, b), b.block(b.copyRange(thenStmts)));
            ctx.getRefactorings().replace(forNode, replacement);

            thenStmts.add(bs);
        }

        private void replaceLoopAndReturn(Statement forNode, Expression iterable, Expression toFind,
                Statement forNextStmt, boolean negate) {
            ASTBuilder b= ctx.getASTBuilder();
            ctx.getRefactorings().replace(forNode, b.return0(newMethod(iterable, toFind, negate, b)));

            if (forNextStmt.equals(getNextSibling(forNode))) {
                ctx.getRefactorings().remove(forNextStmt);
            }
        }

        private boolean maybeReplaceLoopAndVariable(Statement forNode, Expression iterable, Statement uniqueThenStmt,
                Expression toFind) {
            Statement previousStmt= getPreviousStatement(forNode);

            if (previousStmt != null) {
                boolean previousStmtIsPreviousSibling= previousStmt.equals(getPreviousSibling(forNode));
                Assignment as= asExpression(uniqueThenStmt, Assignment.class);
                Pair<Name, Expression> innerInit= decomposeInitializer(as);
                Name initName= innerInit.getFirst();
                Expression init2= innerInit.getSecond();
                Pair<Name, Expression> outerInit= getInitializer(previousStmt);

                if (isSameVariable(outerInit.getFirst(), initName)) {
                    Boolean isPositive= signCollectionContains((BooleanLiteral) init2,
                            (BooleanLiteral) outerInit.getSecond());

                    if (isPositive != null) {
                        replaceLoopAndVariable(forNode, iterable, toFind, previousStmt, previousStmtIsPreviousSibling,
                                initName, isPositive);
                        setResult(false);
                        return false;
                    }
                }
            }

            return true;
        }

        private void replaceLoopAndVariable(Statement forNode, Expression iterable, Expression toFind,
                Statement previousStmt, boolean previousStmtIsPreviousSibling, Name initName, boolean isPositive) {
            ASTBuilder b= ctx.getASTBuilder();

            Statement replacement;
            if (previousStmtIsPreviousSibling && previousStmt instanceof VariableDeclarationStatement) {
                replacement= b.declareStmt(b.type("boolean"), b.move((SimpleName) initName),
                        newMethod(iterable, toFind, isPositive, b));
            } else if (!previousStmtIsPreviousSibling || previousStmt instanceof ExpressionStatement) {
                replacement= b.toStmt(b.assign(b.copy(initName), ASSIGN, newMethod(iterable, toFind, isPositive, b)));
            } else {
                throw new NotImplementedException(forNode);
            }

            ctx.getRefactorings().replace(forNode, replacement);

            if (previousStmtIsPreviousSibling) {
                ctx.getRefactorings().remove(previousStmt);
            }
        }

        private Boolean signCollectionContains(BooleanLiteral innerBl, BooleanLiteral outerBl) {
            if (innerBl != null && outerBl != null && innerBl.booleanValue() != outerBl.booleanValue()) {
                return innerBl.booleanValue();
            }

            return null;
        }

        private Pair<Name, Expression> getInitializer(Statement stmt) {
            if (stmt instanceof VariableDeclarationStatement) {
                return uniqueVariableDeclarationFragmentName(stmt);
            }

            if (stmt instanceof ExpressionStatement) {
                Assignment as= asExpression(stmt, Assignment.class);

                if (hasOperator(as, ASSIGN) && as.getLeftHandSide() instanceof Name) {
                    return Pair.of((Name) as.getLeftHandSide(), as.getRightHandSide());
                }
            }

            return Pair.empty();
        }

        private IfStatement uniqueStmtAs(Statement stmt, Class<IfStatement> stmtClazz) {
            return as(uniqueStmt(asList(stmt)), stmtClazz);
        }

        private Statement uniqueStmt(List<Statement> stmts) {
            return stmts.size() == 1 ? stmts.get(0) : null;
        }

        private BooleanLiteral getReturnedBooleanLiteral(Statement stmt) {
            ReturnStatement rs= as(stmt, ReturnStatement.class);

            if (rs != null) {
                return as(rs.getExpression(), BooleanLiteral.class);
            }

            return null;
        }

        @Override
        public boolean visit(ForStatement node) {
            final ForLoopContent loopContent= iterateOverContainer(node);
            final List<Statement> stmts= asList(node.getBody());

            if (loopContent != null && COLLECTION.equals(loopContent.getContainerType())) {
                if (INDEX.equals(loopContent.getIterationType())) {
                    Expression loopElement= null;
                    IfStatement is;
                    if (stmts.size() == 2) {
                        Pair<Name, Expression> loopVarPair= uniqueVariableDeclarationFragmentName(stmts.get(0));
                        loopElement= loopVarPair.getFirst();
                        MethodInvocation mi= as(loopVarPair.getSecond(), MethodInvocation.class);

                        if (!match(mi, collectionGet(loopContent))
                                || !isSameVariable(mi.getExpression(), loopContent.getContainerVariable())) {
                            return true;
                        }

                        is= as(stmts.get(1), IfStatement.class);
                    } else if (stmts.size() == 1) {
                        is= as(stmts.get(0), IfStatement.class);
                        loopElement= collectionGet(loopContent);
                    } else {
                        return true;
                    }

                    return maybeReplaceWithCollectionContains(node, loopContent.getContainerVariable(), loopElement,
                            is);
                }

                if (ITERATOR.equals(loopContent.getIterationType())) {
                    Expression loopElement= null;
                    IfStatement is;
                    if (stmts.size() == 2) {
                        Pair<Name, Expression> loopVarPair= uniqueVariableDeclarationFragmentName(stmts.get(0));
                        loopElement= loopVarPair.getFirst();
                        MethodInvocation mi= as(loopVarPair.getSecond(), MethodInvocation.class);

                        if (!match(mi, iteratorNext(loopContent))
                                || !isSameVariable(mi.getExpression(), loopContent.getIteratorVariable())) {
                            return true;
                        }

                        is= as(stmts.get(1), IfStatement.class);
                    } else if (stmts.size() == 1) {
                        is= as(stmts.get(0), IfStatement.class);
                        loopElement= iteratorNext(loopContent);
                    } else {
                        return true;
                    }

                    return maybeReplaceWithCollectionContains(node, loopContent.getContainerVariable(), loopElement,
                            is);
                }
            }

            return true;
        }

        private MethodInvocation iteratorNext(final ForLoopContent loopContent) {
            ASTBuilder b= ctx.getASTBuilder();
            return b.invoke(b.copySubtree(loopContent.getIteratorVariable()), "next");
        }

        private MethodInvocation collectionGet(final ForLoopContent loopContent) {
            ASTBuilder b= ctx.getASTBuilder();
            return b.invoke(b.copySubtree(loopContent.getContainerVariable()), "get",
                    b.copySubtree(loopContent.getLoopVariable()));
        }

        private Pair<Name, Expression> uniqueVariableDeclarationFragmentName(Statement stmt) {
            VariableDeclarationFragment vdf= getUniqueFragment(as(stmt, VariableDeclarationStatement.class));

            if (vdf != null) {
                return Pair.of((Name) vdf.getName(), vdf.getInitializer());
            }

            return Pair.empty();
        }
    }
}
