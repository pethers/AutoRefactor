/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2013-2015 Jean-Noël Rouvignac - initial API and implementation
 * Copyright (C) 2016 Sameer Misger - Make SonarQube more happy
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.autorefactor.jdt.internal.corext.dom.ASTNodes;
import org.autorefactor.jdt.internal.corext.dom.SourceLocation;
import org.autorefactor.util.NotImplementedException;
import org.autorefactor.util.Pair;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BlockComment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * See {@link #getDescription()} method.
 *
 * <ul>
 * <li>TODO Remove commented out code</li>
 * <li>TODO Fix malformed/incomplete javadocs</li>
 * <li>TODO Fix typo in comments</li>
 * </ul>
 */
public class CommentsCleanUp extends AbstractCleanUpRule {
    /**
     * Get the name.
     *
     * @return the name.
     */
    public String getName() {
        return MultiFixMessages.CleanUpRefactoringWizard_CommentsCleanUp_name;
    }

    /**
     * Get the description.
     *
     * @return the description.
     */
    public String getDescription() {
        return MultiFixMessages.CleanUpRefactoringWizard_CommentsCleanUp_description;
    }

    /**
     * Get the reason.
     *
     * @return the reason.
     */
    public String getReason() {
        return MultiFixMessages.CleanUpRefactoringWizard_CommentsCleanUp_reason;
    }

    private static final Pattern EMPTY_LINE_COMMENT= Pattern.compile("//\\s*"); //$NON-NLS-1$
    private static final Pattern EMPTY_BLOCK_COMMENT= Pattern.compile("/\\*\\s*(\\*\\s*)*\\*/"); //$NON-NLS-1$
    private static final Pattern EMPTY_JAVADOC= Pattern.compile("/\\*\\*\\s*(\\*\\s*)*\\*/"); //$NON-NLS-1$
    private static final Pattern EMPTY_LINE_AT_START_OF_BLOCK_COMMENT= Pattern.compile("(/\\*)(?:\\s*\\*)+(\\s*\\*)"); //$NON-NLS-1$
    private static final Pattern EMPTY_LINE_AT_START_OF_JAVADOC= Pattern.compile("(/\\*\\*)(?:\\s*\\*)+(\\s*\\*)"); //$NON-NLS-1$
    private static final Pattern EMPTY_LINE_AT_END_OF_BLOCK_COMMENT= Pattern.compile("(?:\\*\\s*)*\\*\\s*(\\*/)"); //$NON-NLS-1$
    private static final Pattern JAVADOC_ONLY_INHERITDOC= Pattern
            .compile("/\\*\\*\\s*(\\*\\s*)*\\{@inheritDoc\\}\\s*(\\*\\s*)*\\*/"); //$NON-NLS-1$
    private static final Pattern ECLIPSE_GENERATED_TODOS= Pattern
            .compile("//\\s*" + "(:?" + "(?:TODO Auto-generated (?:(?:(?:method|constructor) stub)|(?:catch block)))" //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
                    + "|" + "(?:TODO: handle exception)" + ")" + "\\s*"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$ $NON-NLS-4$
    private static final Pattern ECLIPSE_GENERATED_NON_JAVADOC= Pattern
            .compile("/\\*\\s*\\(non-Javadoc\\)\\s*\\*\\s*@see\\s*"); //$NON-NLS-1$
    private static final Pattern ECLIPSE_IGNORE_NON_EXTERNALIZED_STRINGS= Pattern
            .compile("//\\s*\\$NON-NLS-\\d\\$\\s*"); //$NON-NLS-1$
    /**
     * Ignore special instructions to locally enable/disable tools working on java
     * code:
     * <ul>
     * <li>checkstyle</li>
     * <li>JDT</li>
     * </ul>
     */
    private static final Pattern TOOLS_CONTROL_INSTRUCTIONS= Pattern.compile("//\\s*@\\w+:\\w+"); //$NON-NLS-1$
    private static final Pattern JAVADOC_HAS_PUNCTUATION= Pattern.compile("\\.|\\?|!|:"); //$NON-NLS-1$
    private static final Pattern JAVADOC_WITHOUT_PUNCTUATION=
            // @formatter:off
            Pattern.compile("(.*?)" + "(" + "\\s*" + "(?:" + "\\*" + "/" + ")?" + ")", Pattern.DOTALL); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$ $NON-NLS-4$ $NON-NLS-5$ $NON-NLS-6$ $NON-NLS-7$ $NON-NLS-8$ $NON-NLS-9$
    // @formatter:on
    private static final Pattern FIRST_JAVADOC_TAG=
            // @formatter:off
            Pattern.compile("(" + "/\\*\\*" + ")?" + "\\s*" + "(?:\\*\\s*)?" + "@\\w+", Pattern.MULTILINE); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$ $NON-NLS-4$ $NON-NLS-5$ $NON-NLS-6$ $NON-NLS-7$
    // @formatter:on
    private static final Pattern JAVADOC_FIRST_LETTER_LOWERCASE= Pattern
            .compile("(/\\*\\*\\s*(?:(?:\\r|\\n|\\r\\n|\\s)\\s*\\*)*\\s*)(\\w)(.*)", Pattern.DOTALL); //$NON-NLS-1$

    private CompilationUnit astRoot;
    private final List<Pair<SourceLocation, Comment>> comments= new ArrayList<>();

    @Override
    public boolean visit(BlockComment node) {
        final String comment= getComment(node);
        if (EMPTY_BLOCK_COMMENT.matcher(comment).matches()) {
            this.ctx.getRefactorings().remove(node);
            return false;
        }
        final ASTNode nextNode= getNextNode(node);
        if (acceptJavadoc(nextNode) && !betterCommentExist(node, nextNode)) {
            if (ECLIPSE_GENERATED_NON_JAVADOC.matcher(comment).find()) {
                this.ctx.getRefactorings().remove(node);
            } else {
                this.ctx.getRefactorings().toJavadoc(node);
            }
            return false;
        }
        final Matcher emptyLineAtStartMatcher= EMPTY_LINE_AT_START_OF_BLOCK_COMMENT.matcher(comment);
        if (emptyLineAtStartMatcher.find()) {
            return replaceEmptyLineAtStartOfComment(node, emptyLineAtStartMatcher);
        }
        final Matcher emptyLineAtEndMatcher= EMPTY_LINE_AT_END_OF_BLOCK_COMMENT.matcher(comment);
        if (emptyLineAtEndMatcher.find()) {
            return replaceEmptyLineAtEndOfComment(node, emptyLineAtEndMatcher);
        }
        final String replacement= getReplacement(comment, false);
        if (replacement != null && !replacement.equals(comment)) {
            this.ctx.getRefactorings().replace(node, replacement);
            return false;
        }
        return true;
    }

    private String getReplacement(String comment, boolean isJavadoc) {
        int commentLineLength= this.ctx.getJavaProjectOptions().getCommentLineLength();
        String commentNoStartNorEnd= comment.substring(0, comment.length() - 2).substring(isJavadoc ? 3 : 2);
        String commentWithSpaces= commentNoStartNorEnd.replaceAll("\\s*(\\r\\n|\\r|\\n)\\s*\\*", " "); //$NON-NLS-1$ $NON-NLS-2$
        String commentContent= commentWithSpaces.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$ $NON-NLS-2$
        if (commentContent.length() + (isJavadoc ? 7 : 6) < commentLineLength) {
            return (isJavadoc ? "/** " : "/* ") + commentContent + " */"; //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
        }
        return null;
    }

    private ASTNode getNextNode(Comment node) {
        final int nodeEndPosition= node.getStartPosition() + node.getLength();
        final ASTNode coveringNode= getCoveringNode(node);
        final int parentNodeEndPosition= coveringNode.getStartPosition() + coveringNode.getLength();
        final NodeFinder finder= new NodeFinder(coveringNode, nodeEndPosition, parentNodeEndPosition - nodeEndPosition);
        if (node instanceof Javadoc) {
            return finder.getCoveringNode();
        }
        return finder.getCoveredNode();
    }

    private ASTNode getCoveringNode(Comment node) {
        final int start= node.getStartPosition();
        final int length= node.getLength();
        final ASTNode coveringNode= getCoveringNode(start, length);
        if (coveringNode != node) {
            return coveringNode;
        }
        return getCoveringNode(start, length + 1);
    }

    private ASTNode getCoveringNode(int start, int length) {
        final NodeFinder finder= new NodeFinder(this.astRoot, start, length);
        return finder.getCoveringNode();
    }

    @Override
    public boolean visit(Javadoc node) {
        final String comment= getComment(node);
        final boolean isWellFormattedInheritDoc= "/** {@inheritDoc} */".equals(comment); //$NON-NLS-1$
        final Matcher emptyLineAtStartMatcher= EMPTY_LINE_AT_START_OF_JAVADOC.matcher(comment);
        final Matcher emptyLineAtEndMatcher= EMPTY_LINE_AT_END_OF_BLOCK_COMMENT.matcher(comment);
        if (EMPTY_JAVADOC.matcher(comment).matches()) {
            this.ctx.getRefactorings().remove(node);
            return false;
        }
        if (emptyLineAtStartMatcher.find()) {
            return replaceEmptyLineAtStartOfComment(node, emptyLineAtStartMatcher);
        }
        if (emptyLineAtEndMatcher.find()) {
            return replaceEmptyLineAtEndOfComment(node, emptyLineAtEndMatcher);
        }
        if (allTagsEmpty(ASTNodes.tags(node))) {
            this.ctx.getRefactorings().remove(node);
            return false;
        }
        if (!acceptJavadoc(getNextNode(node)) && node.getStartPosition() != 0) {
            this.ctx.getRefactorings().replace(node, comment.replace("/**", "/*")); //$NON-NLS-1$ $NON-NLS-2$
            return false;
        }
        if (JAVADOC_ONLY_INHERITDOC.matcher(comment).matches()) {
            final ASTNode nextNode= getNextNode(node);
            if (hasOverrideAnnotation(nextNode)) {
                // {@inheritDoc} tag is redundant with @Override annotation
                this.ctx.getRefactorings().remove(node);
                return false;
            }

            if (!isWellFormattedInheritDoc) {
                // Put on one line only, to augment vertical density of code
                int startLine= this.astRoot.getLineNumber(node.getStartPosition());
                int endLine= this.astRoot.getLineNumber(node.getStartPosition() + node.getLength());
                if (startLine != endLine) {
                    this.ctx.getRefactorings().replace(node, "/** {@inheritDoc} */"); //$NON-NLS-1$
                    return false;
                }
            }
        } else if (!isWellFormattedInheritDoc && !JAVADOC_HAS_PUNCTUATION.matcher(comment).find()) {
            final String newComment= addPeriodAtEndOfFirstLine(node, comment);
            if (newComment != null) {
                this.ctx.getRefactorings().replace(node, newComment);
                return false;
            }
        } else {
            final Matcher m= JAVADOC_FIRST_LETTER_LOWERCASE.matcher(comment);
            if (m.matches() && Character.isLowerCase(m.group(2).charAt(0))) {
                String newComment= m.group(1) + m.group(2).toUpperCase() + m.group(3);
                if (!newComment.equals(comment)) {
                    this.ctx.getRefactorings().replace(node, newComment);
                    return false;
                }
            }
        }
        if (hasNoTags(node)) {
            final String replacement= getReplacement(comment, true);
            if (replacement != null && !replacement.equals(comment)) {
                this.ctx.getRefactorings().replace(node, replacement);
                return false;
            }
        }
        return true;
    }

    private boolean hasOverrideAnnotation(ASTNode node) {
        if (node instanceof BodyDeclaration) {
            for (IExtendedModifier modifier : ASTNodes.modifiers((BodyDeclaration) node)) {
                return ASTNodes.hasType(getTypeName(modifier), Override.class.getCanonicalName());
            }
        }
        return false;
    }

    private Name getTypeName(IExtendedModifier extendedModifier) {
        if (extendedModifier instanceof MarkerAnnotation) {
            return ((MarkerAnnotation) extendedModifier).getTypeName();
        }
        if (extendedModifier instanceof NormalAnnotation) {
            return ((NormalAnnotation) extendedModifier).getTypeName();
        }
        return null;
    }

    private boolean hasNoTags(Javadoc node) {
        for (TagElement tag : ASTNodes.tags(node)) {
            if (tag.getTagName() != null) {
                return false;
            }
        }
        return true;
    }

    private boolean replaceEmptyLineAtStartOfComment(Comment node, Matcher matcher) {
        final String replacement= matcher.replaceFirst(matcher.group(1) + matcher.group(2));
        this.ctx.getRefactorings().replace(node, replacement);
        return false;
    }

    private boolean replaceEmptyLineAtEndOfComment(Comment node, Matcher matcher) {
        final String replacement= matcher.replaceFirst(matcher.group(1));
        this.ctx.getRefactorings().replace(node, replacement);
        return false;
    }

    private String addPeriodAtEndOfFirstLine(Javadoc node, String comment) {
        String beforeFirstTag= comment;
        String afterFirstTag= ""; //$NON-NLS-1$
        final Matcher m= FIRST_JAVADOC_TAG.matcher(comment);
        if (m.find()) {
            if (m.start() == 0) {
                return null;
            }
            beforeFirstTag= comment.substring(0, m.start());
            afterFirstTag= comment.substring(m.start());
        }
        final Matcher matcher= JAVADOC_WITHOUT_PUNCTUATION.matcher(beforeFirstTag);
        if (matcher.matches()) {
            final List<TagElement> tagElements= ASTNodes.tags(node);
            if (tagElements.size() >= 2) {
                final TagElement firstLine= tagElements.get(0);
                final int relativeStart= firstLine.getStartPosition() - node.getStartPosition();
                final int endOfFirstLine= relativeStart + firstLine.getLength();
                return comment.substring(0, endOfFirstLine) + "." + comment.substring(endOfFirstLine); //$NON-NLS-1$
                // TODO JNR do the replace here, not outside this method
            }
            return matcher.group(1) + "." + matcher.group(2) + afterFirstTag; //$NON-NLS-1$
        }
        return null;
    }

    private boolean allTagsEmpty(List<TagElement> tags) {
        return !anyTagNotEmpty(tags, false);
    }

    /**
     * A tag is considered empty when it does not provide any useful information
     * beyond what is already in the code.
     *
     * @param tags           the tags to look for emptiness
     * @param throwIfUnknown only useful for debugging. For now, default is to not
     *                       remove tag or throw when unknown
     * @return true if any tag is not empty, false otherwise
     */
    private boolean anyTagNotEmpty(List<TagElement> tags, boolean throwIfUnknown) {
        if (tags.isEmpty()) {
            return false;
        }
        for (TagElement tag : tags) {
            if (isNotEmpty(tag, throwIfUnknown)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNotEmpty(TagElement tag, boolean throwIfUnknown) {
        final String tagName= tag.getTagName();
        if (tagName == null || TagElement.TAG_AUTHOR.equals(tagName)
        // || TAG_CODE.equals(tagName)
                || TagElement.TAG_DEPRECATED.equals(tagName)
                // || TAG_DOCROOT.equals(tagName)
                // || TAG_LINK.equals(tagName)
                // || TAG_LINKPLAIN.equals(tagName)
                // || TAG_LITERAL.equals(tagName)
                || TagElement.TAG_RETURN.equals(tagName) || TagElement.TAG_SEE.equals(tagName) || TagElement.TAG_SERIAL.equals(tagName)
                || TagElement.TAG_SERIALDATA.equals(tagName) || TagElement.TAG_SERIALFIELD.equals(tagName) || TagElement.TAG_SINCE.equals(tagName)
                // || TAG_VALUE.equals(tagName)
                || TagElement.TAG_VERSION.equals(tagName)) {
            // TODO JNR a return tag repeating the return type of the method is useless
            return anyTextElementNotEmpty(tag.fragments(), throwIfUnknown);
        }
        if (TagElement.TAG_EXCEPTION.equals(tagName) || TagElement.TAG_PARAM.equals(tagName) || TagElement.TAG_THROWS.equals(tagName)) {
            // TODO JNR a @throws tag repeating the checked exceptions of the method is
            // useless
            return !isTagEmptyOrWithSimpleNameOnly(tag);
        }
        if (!TagElement.TAG_INHERITDOC.equals(tagName) && throwIfUnknown) {
            throw new NotImplementedException(tag, "for tagName " + tagName); //$NON-NLS-1$
        }
        return true;
    }

    private boolean isTagEmptyOrWithSimpleNameOnly(TagElement tag) {
        switch (tag.fragments().size()) {
        case 0:
            return true;
        case 1:
            return tag.fragments().get(0) instanceof SimpleName;
        default:
            return false;
        }
    }

    private boolean anyTextElementNotEmpty(List<?> fragments, boolean throwIfUnknown) {
        for (/* IDocElement */ Object fragment : fragments) {
            if (fragment instanceof TextElement) {
                String text= ((TextElement) fragment).getText();
                if (text != null && !text.isEmpty()) {
                    return true;
                }
            } else if (fragment instanceof TagElement) {
                if (isNotEmpty((TagElement) fragment, throwIfUnknown)) {
                    return true;
                }
            } else if (throwIfUnknown) {
                // It could be one of the following:
                // org.eclipse.jdt.core.dom.MemberRef
                // org.eclipse.jdt.core.dom.MethodRef
                // org.eclipse.jdt.core.dom.Name
                final ASTNode node= fragment instanceof ASTNode ? (ASTNode) fragment : null;
                throw new NotImplementedException(node, fragment);
            }
        }
        return false;
    }

    @Override
    public boolean visit(LineComment node) {
        final String comment= getComment(node);
        if (EMPTY_LINE_COMMENT.matcher(comment).matches() || ECLIPSE_GENERATED_TODOS.matcher(comment).matches()) {
            this.ctx.getRefactorings().remove(node);
            return false;
        }
        if (!TOOLS_CONTROL_INSTRUCTIONS.matcher(comment).matches()
                && !ECLIPSE_IGNORE_NON_EXTERNALIZED_STRINGS.matcher(comment).matches()) {
            final ASTNode nextNode= getNextNode(node);
            final ASTNode previousNode= getPreviousSibling(nextNode);
            if (previousNode != null && isSameLineNumber(node, previousNode)) {
                this.ctx.getRefactorings().toJavadoc(node, previousNode);
                return false;
            }
            if (acceptJavadoc(nextNode) && !betterCommentExist(node, nextNode)) {
                this.ctx.getRefactorings().toJavadoc(node, nextNode);
                return false;
            }
        }
        return true;
    }

    private boolean isSameLineNumber(LineComment node, ASTNode previousNode) {
        final CompilationUnit cu= (CompilationUnit) previousNode.getRoot();
        final int lineNb1= cu.getLineNumber(node.getStartPosition());
        final int lineNb2= cu.getLineNumber(previousNode.getStartPosition());
        return lineNb1 == lineNb2;
    }

    private ASTNode getPreviousSibling(ASTNode node) {
        boolean isPrevious= true;
        if (node != null && node.getParent() instanceof TypeDeclaration) {
            final TypeDeclaration typeDecl= (TypeDeclaration) node.getParent();

            final TreeMap<Integer, ASTNode> nodes= new TreeMap<>();
            addAll(nodes, typeDecl.getFields());
            addAll(nodes, typeDecl.getMethods());
            addAll(nodes, typeDecl.getTypes());

            if (isPrevious) {
                SortedMap<Integer, ASTNode> entries= nodes.headMap(node.getStartPosition());
                if (!entries.isEmpty()) {
                    return entries.get(entries.lastKey());
                }
            } else {
                SortedMap<Integer, ASTNode> entries= nodes.tailMap(node.getStartPosition());
                if (!entries.isEmpty()) {
                    return entries.get(entries.firstKey());
                }
            }
        }
        return null;
    }

    private <T extends ASTNode> void addAll(TreeMap<Integer, ASTNode> nodeMap, T[] nodes) {
        for (T node : nodes) {
            nodeMap.put(node.getStartPosition(), node);
        }
    }

    private boolean betterCommentExist(Comment comment, ASTNode nodeWhereToAddJavadoc) {
        if (hasJavadoc(nodeWhereToAddJavadoc)) {
            return true;
        }

        final SourceLocation nodeLoc= new SourceLocation(nodeWhereToAddJavadoc);
        SourceLocation bestLoc= new SourceLocation(comment);
        Comment bestComment= comment;
        for (Iterator<Pair<SourceLocation, Comment>> iter= this.comments.iterator(); iter.hasNext();) {
            final Pair<SourceLocation, Comment> pair= iter.next();
            final SourceLocation newLoc= pair.getFirst();
            final Comment newComment= pair.getSecond();
            if (newLoc.compareTo(bestLoc) < 0) {
                // Since comments are visited in ascending order,
                // we can forget this comment.
                iter.remove();
                continue;
            }
            if (nodeLoc.compareTo(newLoc) < 0) {
                break;
            }
            if (bestLoc.compareTo(newLoc) < 0
                    && (!(newComment instanceof LineComment) || !(bestComment instanceof LineComment))) {
                // New comment is a BlockComment or a Javadoc
                bestLoc= newLoc;
                bestComment= newComment;
                continue;
            }
        }
        return bestComment != null && bestComment != comment;
    }

    private boolean hasJavadoc(ASTNode node) {
        if (node instanceof BodyDeclaration) {
            return ((BodyDeclaration) node).getJavadoc() != null;
        }
        if (node instanceof PackageDeclaration) {
            return ((PackageDeclaration) node).getJavadoc() != null;
        }
        return false;
    }

    private boolean acceptJavadoc(final ASTNode node) {
        // PackageDeclaration node accept javadoc in package-info.java files,
        // but they are useless everywhere else.
        return node instanceof BodyDeclaration
                || (node instanceof PackageDeclaration && "package-info.java".equals(ASTNodes.getFileName(node))); //$NON-NLS-1$
    }

    @Override
    public boolean visit(CompilationUnit node) {
        comments.clear();

        this.astRoot= node;
        for (Comment comment : ASTNodes.getCommentList(astRoot)) {
            comments.add(Pair.of(new SourceLocation(comment), comment));
        }

        for (Comment comment : ASTNodes.getCommentList(astRoot)) {
            if (comment.isBlockComment()) {
                final BlockComment bc= (BlockComment) comment;
                bc.accept(this);
            } else if (comment.isLineComment()) {
                final LineComment lc= (LineComment) comment;
                lc.accept(this);
            } else if (comment.isDocComment()) {
                final Javadoc jc= (Javadoc) comment;
                jc.accept(this);
            } else {
                throw new NotImplementedException(comment);
            }
        }
        return true;
    }

    private String getComment(Comment node) {
        final String source= this.ctx.getSource(node);
        final int start= node.getStartPosition();
        return source.substring(start, start + node.getLength());
    }
}
