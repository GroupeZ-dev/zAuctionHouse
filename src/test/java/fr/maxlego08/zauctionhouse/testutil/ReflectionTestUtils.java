package fr.maxlego08.zauctionhouse.testutil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Acces par reflexion aux champs et methodes prives des classes testees.
 * <p>
 * Les boutons zMenu du plugin n'exposent presque aucun getter : les loaders sont donc verifies
 * en lisant directement les champs du bouton produit. Ne jamais utiliser ces helpers pour
 * contourner un comportement de production, uniquement pour OBSERVER l'etat construit.
 */
public final class ReflectionTestUtils {

    private ReflectionTestUtils() {
    }

    /**
     * Lit un champ (meme prive, meme herite) de l'objet.
     */
    @SuppressWarnings("unchecked")
    public static <T> T field(Object target, String fieldName) {
        Field field = findField(target.getClass(), fieldName);
        try {
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read field " + fieldName + " of " + target.getClass(), exception);
        }
    }

    /**
     * Ecrit un champ d'instance (meme prive, meme final) de l'objet.
     */
    public static void setField(Object target, String fieldName, Object value) {
        Field field = findField(target.getClass(), fieldName);
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to write field " + fieldName + " of " + target.getClass(), exception);
        }
    }

    /**
     * Invoque une methode (meme privee) de l'objet, resolue par nom et types de parametres.
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        Method method = findMethod(target.getClass(), methodName, parameterTypes);
        try {
            method.setAccessible(true);
            return (T) method.invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Unable to invoke " + methodName + " on " + target.getClass(), cause);
        }
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("No field " + fieldName + " in " + type);
    }

    private static Method findMethod(Class<?> type, String methodName, Class<?>[] parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("No method " + methodName + " in " + type);
    }
}
