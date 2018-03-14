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

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Walk over a jar and produce a new version that includes null enforcement
 * checks.
 * 
 * @author Andy Clement
 */
public class JarProcessor {

	List<String> nonNullApiPackages = new ArrayList<>();

	private String inputJarFile;
	
	// Default will have nullchecked inserted into input name (e.g. input = foo.jar output = foo.nullchecked.jar)
	private String outputJarFile;

	public JarProcessor(String inputJarFile) {
		this.inputJarFile = inputJarFile;
	}

	public void setOutputJarFile(String outputJarFile) {
		this.outputJarFile = outputJarFile;
	}

	public void process() {
		ensureOutputJarFileSet();
		try {
			findNonNullAPIMarkedPackages(inputJarFile);
			ZipFile zin = new ZipFile(inputJarFile);
			OutputStream fos = new FileOutputStream(outputJarFile);
			ZipOutputStream zos = new ZipOutputStream(fos);
			Enumeration<? extends ZipEntry> entries = zin.entries();
			while (entries.hasMoreElements()) {
				ZipEntry ze = entries.nextElement();
				if (isClass(ze)) {
					byte[] newClass = addNullEnforcement(zin.getInputStream(ze));
					zos.putNextEntry(new ZipEntry(ze.getName()));
					copyData(new ByteArrayInputStream(newClass), zos);
					zos.closeEntry();
				} else {
					zos.putNextEntry(new ZipEntry(ze.getName()));
					copyData(zin.getInputStream(ze), zos);
					zos.closeEntry();
				}
			}
			zos.close();
			zin.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private byte[] addNullEnforcement(InputStream inputStream) throws Exception {
		ClassReader cr = new ClassReader(inputStream);
		ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);
		AddNullEnforcementClassVisitor cv = new AddNullEnforcementClassVisitor(nonNullApiPackages, cw);
		cr.accept(cv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}

	private void copyData(InputStream is, OutputStream os) {
		try {
			byte[] bs = new byte[100000];
			int i;
			while ((i = is.read(bs)) != -1) {
				os.write(bs, 0, i);
			}
			is.close();
		} catch (IOException ioe) {
			throw new IllegalStateException("Unable to copy data from input to output", ioe);
		}

	}

	private boolean isClass(ZipEntry ze) {
		if (ze.getName().endsWith(".class")) {
			return true;
		}
		return false;
	}

	private void ensureOutputJarFileSet() {
		if (outputJarFile == null) {
			outputJarFile = inputJarFile.substring(0, inputJarFile.length() - 4) + ".nullenforced.jar";
		}
	}

	private ClassFileInfo fetchPackageInfo(InputStream is) {
		try {
			ClassReader cr = new ClassReader(is);
			AnnoCollector ac = new AnnoCollector();
			cr.accept(ac, 0);
			return new ClassFileInfo(ac.getName(), ac.getAnnotations());
		} catch (Exception e) {
			throw new IllegalStateException("Unable to fetch annotations");
		}
	}

	static class AnnoCollector extends ClassVisitor {

		String name;

		List<String> annotations = new ArrayList<>();

		public List<String> getAnnotations() {
			return annotations;
		}

		public String getName() {
			return name;
		}

		public AnnoCollector() {
			super(Opcodes.ASM6);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName,
				String[] interfaces) {
			this.name = name;
			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			annotations.add(descriptor);
			return super.visitAnnotation(descriptor, visible);
		}

	}

	private void findNonNullAPIMarkedPackages(String inputJarFile) throws Exception {
		try (JarFile jf = new JarFile(inputJarFile)) {
			Enumeration<JarEntry> entries = jf.entries();
			while (entries.hasMoreElements()) {
				JarEntry nextElement = entries.nextElement();
				String name = nextElement.getName();
				if (name.endsWith("package-info.class")) {
					recordPackage(fetchPackageInfo(jf.getInputStream(nextElement)));
				// } else if (nextElement.isDirectory()) {
				// } else if (name.endsWith(".class")) {
				}
			}
		}
	}
	
	private void recordPackage(ClassFileInfo ci) {
		if (ci.annotations.contains(Constants.nonNullApiDescriptor)) {
			nonNullApiPackages.add(ci.getPackageName());
		}
	}

	static class ClassFileInfo {

		String name;

		List<String> annotations;

		public ClassFileInfo(String name, List<String> annotations) {
			this.name = name;
			this.annotations = annotations;
		}

		public String getPackageName() {
			int i = name.lastIndexOf("/");
			if (i == -1) {
				return "";
			}
			return name.substring(0,i).replace("/", ".");
		}

		public String toString() {
			return "ci: " + name + " " + annotations;
		}

	}
}
