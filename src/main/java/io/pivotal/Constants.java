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

/**
 * 
 * @author Andy Clement
 */
public class Constants {

	// TODO make configurable or use the type descriptor aliases to work out valid
	// options here (may be more than one for each)

	public final static String nonNullApiDescriptor = toDescriptor(System.getProperty("nonnullapi","reactor.util.annotation.NonNullApi"));

	public final static String notNullDescriptor = toDescriptor(System.getProperty("notnull","org.jetbrains.annotations.NotNull"));

	public final static String nullableDescriptor = toDescriptor(System.getProperty("nullable","reactor.util.annotation.Nullable"));

	public final static String toDescriptor(String type) {
		return "L" + type.replace(".", "/") + ";";
	}
}
