/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Stephan Herrmann - Contribution for
 *								Bug 429958 - [1.8][null] evaluate new DefaultLocation attribute of @NonNullByDefault
 *******************************************************************************/
package org.eclipse.jdt.internal.compiler.ast;

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.impl.Constant;
import org.eclipse.jdt.internal.compiler.lookup.ArrayBinding;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.ProblemReasons;
import org.eclipse.jdt.internal.compiler.lookup.Scope;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;

public class JavadocImplicitTypeReference extends TypeReference {

	public char[] token;

	public JavadocImplicitTypeReference(char[] name, int pos) {
		super();
		this.token = name;
		this.sourceStart = pos;
		this.sourceEnd = pos;
	}

	@Override
	public TypeReference augmentTypeWithAdditionalDimensions(int additionalDimensions, Annotation[][] additionalAnnotations, boolean isVarargs) {
		return null;
	}

	@Override
	protected TypeBinding getTypeBinding(Scope scope) {
		this.constant = Constant.NotAConstant;
		return this.resolvedType = scope.enclosingReceiverType();
	}

	@Override
	public char[] getLastToken() {
		return this.token;
	}

	@Override
	public char[][] getTypeName() {
		if (this.token != null) {
			char[][] tokens = { this.token };
			return tokens;
		}
		return null;
	}
	@Override
	public boolean isThis() {
		return true;
	}

	/*
	 * Resolves type on a Block, Class or CompilationUnit scope.
	 * We need to modify resoling behavior to avoid raw type creation.
	 */
	@Override
	protected TypeBinding internalResolveType(Scope scope, int location) {
		// handle the error here
		this.constant = Constant.NotAConstant;
		if (this.resolvedType != null) { // is a shared type reference which was already resolved
			if (this.resolvedType.isValidBinding()) {
				return this.resolvedType;
			} else {
				switch (this.resolvedType.problemId()) {
					case ProblemReasons.NotFound :
					case ProblemReasons.NotVisible :
						TypeBinding type = this.resolvedType.closestMatch();
						return type;
					default :
						return null;
				}
			}
		}
		boolean hasError;
		TypeBinding type = this.resolvedType = getTypeBinding(scope);
		if (type == null) {
			return null; // detected cycle while resolving hierarchy
		} else if ((hasError = !type.isValidBinding())== true) {
			reportInvalidType(scope);
			switch (type.problemId()) {
				case ProblemReasons.NotFound :
				case ProblemReasons.NotVisible :
					type = type.closestMatch();
					if (type == null) return null;
					break;
				default :
					return null;
			}
		}
		if (type.isArrayType() && ((ArrayBinding) type).leafComponentType == TypeBinding.VOID) {
			scope.problemReporter().cannotAllocateVoidArray(this);
			return null;
		}
		if (isTypeUseDeprecated(type, scope)) {
			reportDeprecatedType(type, scope);
		}
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=209936
		// raw convert all enclosing types when dealing with Javadoc references
		if (type.isGenericType() || type.isParameterizedType()) {
			type = scope.environment().convertToRawType(type, true /*force the conversion of enclosing types*/);
		}

		if (hasError) {
			// do not store the computed type, keep the problem type instead
			return type;
		}
		return this.resolvedType = type;
	}

	@Override
	protected void reportInvalidType(Scope scope) {
		scope.problemReporter().javadocInvalidType(this, this.resolvedType, scope.getDeclarationModifiers());
	}
	@Override
	protected void reportDeprecatedType(TypeBinding type, Scope scope) {
		scope.problemReporter().javadocDeprecatedType(type, this, scope.getDeclarationModifiers());
	}

	@Override
	public void traverse(ASTVisitor visitor, BlockScope scope) {
		visitor.visit(this, scope);
		visitor.endVisit(this, scope);
	}

	@Override
	public void traverse(ASTVisitor visitor, ClassScope scope) {
		visitor.visit(this, scope);
		visitor.endVisit(this, scope);
	}

	@Override
	public StringBuilder printExpression(int indent, StringBuilder output) {
		return new StringBuilder();
	}
}
