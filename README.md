# Venuzdonoa
Bypass for Jigsaw module system, without Unsafe

Named after Venuzdonoa, the Abolisher of Reason

Example:
```java
Method m = Class.forName("jdk.internal.misc.Unsafe").getDeclaredMethod("getUnsafe");
MethodHandle mh = makeMethodAccessor(m);
System.out.println(mh.invoke()); // Should print jdk.internal.misc.Unsafe@hashCode
```

Does it work with fields? Yes  
Does it work with constructors? Yes

# But how?
By default, JVM will try to lookup native method implementations in several ways:  
If the class is loaded in the bootstrap class loader, JVM will attempt to resolve some specially pre-registered methods so that the VM does not end up in boot loop. If it doesn't find specially pre-registered implementation, `ClassLoader::findNative` is called.  
If the class is loaded in any other class loader, JVM will always (!) delegate to `ClassLoader::findNative` method, which we abuse.  
By loading in some class that has its native methods implemented elsewhere, we can force JVM to register any method we want! Therefore, NativeMethodAccessorImpl is our victim, since its implementation is located in `java.dll/libjava.(so/dylib)`.
