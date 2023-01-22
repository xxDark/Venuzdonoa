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
