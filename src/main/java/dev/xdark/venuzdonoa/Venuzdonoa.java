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
            // Step 1: load java.dll into the class loader
            // Loading it into system class loader wont work,
            // see ClassLoader::findNative
            ClassWriter writer = new ClassWriter(0);
            writer.visit(V1_6, ACC_PUBLIC, "dev/xdark/venuzdonoa/LibraryLoader", null, "java/lang/Object", null);
            MethodVisitor mv = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "load", "()V", null, null);
            mv.visitCode();
            Path path = Paths.get(System.getProperty("java.home"));
            Path jre = path.resolve("jre");
            if (Files.isDirectory(jre)) {
                path = jre;
            }
            String os = System.getProperty("os.name").toLowerCase(Locale.US);
            if (os.contains("win")) {
                path = path.resolve("bin/java.dll");
            } else if (os.contains("osx")) {
                path = path.resolve("lib/libjava.dylib");
            } else {
                path = path.resolve("lib/libjava.so");
            }
            mv.visitCode();
            mv.visitLdcInsn(path.toString());
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "load", "(Ljava/lang/String;)V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 0);
            mv.visitEnd();
            cl.defineClass(writer.toByteArray()).getMethod("load").invoke(null);
            // Step 2: load NativeMethodAccessorImpl
            String className;
            URL url = ClassLoader.getSystemResource("jdk/internal/reflect/NativeMethodAccessorImpl.class");
            if (url == null) {
                className = "sun/reflect/NativeMethodAccessorImpl";
            } else {
                className = "jdk/internal/reflect/NativeMethodAccessorImpl";
            }
            writer = new ClassWriter(0);
            writer.visit(V1_6, ACC_PUBLIC, className, null, "java/lang/Object", null);
            mv = writer.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_NATIVE, "invoke0", "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
            mv.visitMaxs(0, 3);
            Class<?> k = cl.defineClass(writer.toByteArray());
            MH = MethodHandles.lookup().findStatic(k, "invoke0", MethodType.methodType(Object.class, Method.class, Object.class, Object[].class));
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private Venuzdonoa() {
    }

    public static MethodHandle makeMethodAccessor(Method m) {
        MethodHandle mh = MH.bindTo(m);
        MethodType type = MethodType.methodType(m.getReturnType(), m.getParameterTypes());
        if (Modifier.isStatic(m.getModifiers())) {
            mh = mh.bindTo(null);
        } else {
            type = type.insertParameterTypes(0, Object.class);
        }
        mh = mh.asCollector(Object[].class, m.getParameterCount());
        return mh.asType(type);
    }

    private static final class InnerClassLoader extends ClassLoader {

        Class<?> defineClass(byte[] b) {
            return defineClass(null, b, 0, b.length);
        }
    }
}
