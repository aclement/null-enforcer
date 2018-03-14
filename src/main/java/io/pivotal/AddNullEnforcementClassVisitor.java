/*
 * Copyright (c) 2018 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pivotal;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 
 * @author Andy Clement
 */
public class AddNullEnforcementClassVisitor extends ClassVisitor implements Opcodes {

	private int nullEnforcementCount = 0;
	private List<String> nonNullApiPackages;
	private boolean isNonNullMarkedPackage;
	private boolean ignoreClassFromKotlinSource = false;
	private String currentType;
	private String currentMethodAndDescriptor;

	public AddNullEnforcementClassVisitor(List<String> nonNullApiPackages, ClassWriter cw) {
		super(ASM6, cw);
		this.nonNullApiPackages = nonNullApiPackages;
	}

	public int getNullEnforcementCount() {
		return nullEnforcementCount;
	}

	@Override
	public void visitSource(String source, String debug) {
		if (source != null && source.endsWith(".kt")) {
			ignoreClassFromKotlinSource = true;
		}
		super.visitSource(source, debug);
	}
	
	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.currentType = name;
		// Compute whether type is in package marked with NonNullAPI
		String pkgName = getPackageName(name);
		isNonNullMarkedPackage = nonNullApiPackages.contains(pkgName);
		super.visit(version, access, name, signature, superName, interfaces);
	}

	private String getPackageName(String name) {
		int i = name.lastIndexOf("/");
		if (i == -1) {
			return "";
		}
		return name.substring(0, i).replace("/", ".");
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
		if (ignoreClassFromKotlinSource) {
			return mv;
		}
		currentMethodAndDescriptor = name + descriptor;
		return new AddNullEnforcementMethodVisitor(mv, (access & ACC_STATIC) != 0, getParameterCount(descriptor));
	}

	private int getParameterCount(String descriptor) {
		// Example descriptor: (JLreactor/util/function/Tuple2;I)Ljava/lang/Object; want
		// count of 3
		// 1. grab the params piece between parens
		String params = descriptor.substring(1, descriptor.indexOf(")"));
		// 2. walk Ls and primitives and arrays
		int pcount = 0;
		int p = 0;
		while (p < params.length()) {
			pcount++;
			if (params.charAt(p)=='[') {
				// skip to whatever is after the [
				while (params.charAt(p)=='[') { p++; }
			}
			if (params.charAt(p) == 'L') {
				p = params.indexOf(";", p) + 1;
			} else {
				p++; // primitive
			}
		}
		return pcount;
	}

	class AddNullEnforcementMethodVisitor extends MethodVisitor {

		private List<Integer> parametersNotToNullCheck = new ArrayList<>();
		private List<Integer> parametersToNullCheck = new ArrayList<>();
		private boolean isStatic = false;
		private int parameterCount;

		public AddNullEnforcementMethodVisitor(MethodVisitor mv, boolean isStatic, int parameterCount) {
			super(ASM6, mv);
			this.isStatic = isStatic;
			this.parameterCount = parameterCount;
		}

		@Override
		public void visitCode() {
			if (isNonNullMarkedPackage) {
				// Check everything except those marked @Nullable
				for (int p = 0; p < parameterCount; p++) {
					System.out.println("Adding requireNonNull checks to parameters except those marked @Nullable :"+currentType+"."+currentMethodAndDescriptor);
					int actualParam = p + (isStatic?0:1);
					if (!parametersNotToNullCheck.contains(p)) {
						mv.visitVarInsn(ALOAD, actualParam);
						mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "requireNonNull",
								"(Ljava/lang/Object;)Ljava/lang/Object;", false);
						nullEnforcementCount++;
					}
				}
			} else {
				if (parametersToNullCheck.size() != 0) {
					// Check everything marked @NotNull
					System.out
							.println("Adding requireNonNull checks to some parameters :"+currentType+"."+currentMethodAndDescriptor);
					for (Integer p : parametersToNullCheck) {
						mv.visitVarInsn(ALOAD, p);
						mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "requireNonNull",
								"(Ljava/lang/Object;)Ljava/lang/Object;", false);
						nullEnforcementCount++;
					}
				}
			}
			super.visitCode();
		}

		@Override
		public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
			if (descriptor.equals(Constants.notNullDescriptor)) {
				if (isNonNullMarkedPackage) {
					System.out.println("Why is parameter #"+parameter+" on "+currentType+"."+currentMethodAndDescriptor+" marked @NotNull in a @NonNullAPI marked package?");
				}
				parametersToNullCheck.add(parameter);
			} else if (descriptor.equals(Constants.nullableDescriptor)) {
				parametersNotToNullCheck.add(parameter);
			}
			return super.visitParameterAnnotation(parameter, descriptor, visible);
		}
	}
}