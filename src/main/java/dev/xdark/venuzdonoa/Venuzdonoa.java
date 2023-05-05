package dev.xdark.venuzdonoa;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.objectweb.asm.Opcodes.*;

public final class Venuzdonoa {
    private static final MethodHandle MH;

    static {
        try {
            InnerClassLoader cl = new InnerClassLoader();
            // Step 1: find correct class name for NativeMethodAccessorImpl, based on current jre
            // this is important since the java shared library only contains native method impls for that specific class,
            // and we imitate that class to get hold of the invoke0() method
            String className;
            URL url = ClassLoader.getSystemResource("jdk/internal/reflect/NativeMethodAccessorImpl.class");
            if (url == null) {
                // sun jre
                className = "sun/reflect/NativeMethodAccessorImpl";
            } else {
                // openjdk or any other sensible jre
                className = "jdk/internal/reflect/NativeMethodAccessorImpl";
            }
            ClassWriter writer = new ClassWriter(0);
            // start remaking a version of NativeMethodAccessorIpl
            writer.visit(V1_6, ACC_PUBLIC, className, null, "java/lang/Object", null);

            MethodVisitor methodVisitor = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "load", "()V", null, null);
            Path toLoad = Paths.get(System.getProperty("java.home"));
            Path jre = toLoad.resolve("jre");
            if (Files.isDirectory(jre)) {
                toLoad = jre;
            }
            String os = System.getProperty("os.name").toLowerCase(Locale.US);
            if (os.contains("win")) {
                // {java.home}(/jre)?/bin/java.dll
                toLoad = toLoad.resolve("bin/java.dll");
            } else if (os.contains("osx")) {
                // {java.home}(/jre)?/bin/libjava.dylib
                toLoad = toLoad.resolve("lib/libjava.dylib");
            } else {
                // {java.home}(/jre)?/bin/libjava.so
                toLoad = toLoad.resolve("lib/libjava.so");
            }
            // loads the java shared library we found earlier into our NativeMethodAccessorImpl
            methodVisitor.visitCode();
            methodVisitor.visitLdcInsn(toLoad.toString());
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "load", "(Ljava/lang/String;)V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(1, 0);
            methodVisitor.visitEnd();

            // public static native java.lang.Object invoke0(java.lang.reflect.Method, java.lang.Object, java.lang.Object[]);
            // this is the target method we want to get hold of
            methodVisitor = writer.visitMethod(
                ACC_PUBLIC | ACC_STATIC | ACC_NATIVE,
                "invoke0",
                "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                null,
                null
            );
            methodVisitor.visitMaxs(0, 3);
            Class<?> k = cl.defineClass(writer.toByteArray());
            // Step 2: load java.dll into the class loader
            // Loading it into system class loader won't work,
            // see ClassLoader::findNative
            k.getMethod("load").invoke(null);
            MH = MethodHandles.lookup().findStatic(k, "invoke0", MethodType.methodType(Object.class, Method.class, Object.class, Object[].class));
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private Venuzdonoa() {
    }

    /**
     * Makes an invoker for {@code m}, entirely bypassing the module system
     *
     * @param m Method to make an invoker for
     *
     * @return MethodHandle that, when invoked, invokes {@code m}
     */
    public static MethodHandle makeMethodAccessor(Method m) {
        // set first argument of our invoke0 to m
        MethodHandle mh = MH.bindTo(m);
        MethodType type = MethodType.methodType(m.getReturnType(), m.getParameterTypes());
        if (Modifier.isStatic(m.getModifiers())) {
            // set second one to null, this is a static method
            mh = mh.bindTo(null);
        } else {
            // virtual method, first slot has instance
            type = type.insertParameterTypes(0, Object.class);
        }
        // collect `m.getParameterCount()` trailing args into the args array,
        // first arg (if present) gets filled into the instance, otherwise set to null by previous bindTo(null)
        mh = mh.asCollector(Object[].class, m.getParameterCount());
        return mh.asType(type);
    }

    private static final class InnerClassLoader extends ClassLoader {

        Class<?> defineClass(byte[] b) {
            return defineClass(null, b, 0, b.length);
        }
    }
}
