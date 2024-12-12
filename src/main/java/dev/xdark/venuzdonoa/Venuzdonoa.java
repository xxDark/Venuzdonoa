package dev.xdark.venuzdonoa;

import lombok.SneakyThrows;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

import static org.objectweb.asm.Opcodes.*;

public final class Venuzdonoa {

    private static final MethodHandle METHOD_HANDLE;

    static {
        initialize: try {
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

            InnerClassLoader innerClassLoader = new InnerClassLoader();

            /*
             * STEP: 1
             * Load libjava binary in our custom class loader.
             * Loading the binary in the system class loader won't work
             */
            ClassWriter writer = new ClassWriter(0);
            writer.visit(V1_6, ACC_PUBLIC, "dev/xdark/venuzdonoa/LibraryLoader", null, "java/lang/Object", null);
            MethodVisitor mv = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "load", "()V", null, null);

            /*
             * Add method implementation for dummy class method: load
             * System.load(...);
             */
            mv.visitCode();
            mv.visitLdcInsn(path.toString());
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "load", "(Ljava/lang/String;)V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 0);
            mv.visitEnd();

//            class LibraryLoader {
//                public static void load() {
//                    System.load("lib/libjava.so");
//                }
//            }

            /*
             * Actually load the binary by invoking the method
             */
            try {
                innerClassLoader.defineClass(writer.toByteArray()).getMethod("load").invoke(null);
            } catch (LinkageError | InvocationTargetException e) {
                String className = System.getProperty("Venuzdonoa");
                Class<?> venuzdonoaClass = Class.forName(className);

                Field methodHandler = getMethodHandleField(venuzdonoaClass);

                METHOD_HANDLE = (MethodHandle) methodHandler.get(null);
                break initialize;
            }


            /*
             * STEP 2:
             * Load custom NativeMethodAccessorImpl implementation.
             * Get the correct path
             */
            String className;
            URL url = ClassLoader.getSystemResource("jdk/internal/reflect/NativeMethodAccessorImpl.class");
            if (url == null) {
                className = "sun/reflect/NativeMethodAccessorImpl";
            } else {
                className = "jdk/internal/reflect/NativeMethodAccessorImpl";
            }

            /*
             * Create own NativeMethodAccessorImpl that only contains the invoke0 method.
             * Make the invoke0 method public while we are at it.
             * We don't care about the rest
             */
            ClassWriter writer2 = new ClassWriter(0);
            writer2.visit(V1_6, ACC_PUBLIC, className, null, "java/lang/Object", null);
            MethodVisitor mv2 = writer2.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_NATIVE, "invoke0", "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
            mv2.visitMaxs(0, 3);
            Class<?> dummyClazz = innerClassLoader.defineClass(writer2.toByteArray());

            /*
             * Create method handle for our (now) public invoke0 method.
             */
            METHOD_HANDLE = MethodHandles.lookup().findStatic(dummyClazz, "invoke0", MethodType.methodType(Object.class, Method.class, Object.class, Object[].class));

            /*
             * Make MethodHandle available for other (maybe shadowed) instances of Venuzdonoa
             */
            System.setProperty("Venuzdonoa", Venuzdonoa.class.getName());
            getMethodHandleField(Venuzdonoa.class).setAccessible(true);

        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static Field getMethodHandleField(Class<?> venuzdonoaClass) {
        return Arrays.stream(venuzdonoaClass.getDeclaredFields()).filter(field -> field.getType().equals(MethodHandle.class))
                .findFirst()
                .orElseThrow(RuntimeException::new);
    }

    @SneakyThrows
    @SuppressWarnings("unused")
    public static MethodHandle makeMethodAccessor(Method method) {
        MethodHandle methodHandle = METHOD_HANDLE.bindTo(method);
        MethodType type = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
        if (Modifier.isStatic(method.getModifiers())) {
            methodHandle = methodHandle.bindTo(null);
        } else {
            type = type.insertParameterTypes(0, Object.class);
        }
        methodHandle = methodHandle.asCollector(Object[].class, method.getParameterCount());
        return methodHandle.asType(type);
    }

    private static final class InnerClassLoader extends ClassLoader {

        Class<?> defineClass(byte[] b) {
            return defineClass(null, b, 0, b.length);
        }

    }
}
