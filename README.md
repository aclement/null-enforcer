# Null-enforcer

Inserts `Object.requireNonNull()` checks into code based on the existence of @Nullable/@NotNull annotations

    java AddNullEnforcement <pathToJar>
    
Will produces a variant of pathToJar with 'nullenforced' inserted into the name that includes the checks.

Example

	java AddNullEnforcement reactor-core-3.1.5.RELEASE.jar
	
produces `reactor-core-3.1.5.RELEASE.nullenforced.jar`.  The inserted checks looking like:

    javap -private -verbose -classpath reactor-core-3.1.5.RELEASE.nullenforced.jar reactor/util/Loggers\$Slf4JLogger
    

    public void warn(java.lang.String);
    descriptor: (Ljava/lang/String;)V
    flags: ACC_PUBLIC
    Code:
      stack=3, locals=2, args_size=2
         0: aload_1
         1: invokestatic  #99                 // Method java/util/Objects.requireNonNull:(Ljava/lang/Object;)Ljava/lang/Object;
         4: aload_0
         5: getfield      #2                  // Field logger:Lorg/slf4j/Logger;
         
Notes:

- see Constants for the currently recognized NotNull/Nullable/NonNullAPI annotations
- it will recognize packages marked @NonNullAPI and in those cases insert checks for all parameters within types in
  those packages not marked @Nullable
- when running it, because stack maps must be recomputed, you need to include the classpath entries used to build the jar
  being 'modified'
- hasn't had a lot of testing!
- Should include parameter names in null checks but needs more work for that
