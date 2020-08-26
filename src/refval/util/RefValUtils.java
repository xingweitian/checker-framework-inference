package refval.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.BugInCF;

import com.sun.source.tree.LiteralTree;

import refval.qual.RefVal;

/**
 * Utility class for RefVal type system.
 * 
 * @author jianchu
 *
 */
public class RefValUtils {

    public static String[] getTypeNames(AnnotationMirror type) {
        return getReferenceValue(type, "typeNames");
    }

    public static String[] getTypeNameRoots(AnnotationMirror type) {
        return getReferenceValue(type, "typeNameRoots");
    }

    private static String[] getReferenceValue(AnnotationMirror type, String valueName) {
        List<String> allTypesList = AnnotationUtils.getElementValueArray(type, valueName, String.class,
                true);
        // types in this list is org.checkerframework.framework.util.AnnotationBuilder.
        String[] allTypesInArray = new String[allTypesList.size()];
        int i = 0;
        for (Object o : allTypesList) {
            allTypesInArray[i] = o.toString();
            i++;
        }
        return allTypesInArray;
    }

    public static AnnotationMirror createRefValAnnotationForByte(String[] dataType,
            ProcessingEnvironment processingEnv) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, RefVal.class);
        builder.setValue("typeNameRoots", dataType);
        return builder.build();
    }

    private static AnnotationMirror createRefValAnnotation(final Set<String> datatypes,
            final AnnotationBuilder builder) {
        String[] datatypesInArray = new String[datatypes.size()];
        int i = 0;
        for (String datatype : datatypes) {
            datatypesInArray[i] = datatype.toString();
            i++;
        }
        builder.setValue("typeNames", datatypesInArray);
        return builder.build();
    }

    private static AnnotationMirror createRefValAnnotationWithoutName(final Set<String> roots,
            final AnnotationBuilder builder) {
        String[] datatypesInArray = new String[roots.size()];
        int i = 0;
        for (String datatype : roots) {
            datatypesInArray[i] = datatype.toString();
            i++;
        }
        builder.setValue("typeNameRoots", datatypesInArray);
        return builder.build();
    }

    public static AnnotationMirror createRefValAnnotation(Set<String> datatypes,
            ProcessingEnvironment processingEnv) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, RefVal.class);

        return createRefValAnnotation(datatypes, builder);
    }

    public static AnnotationMirror createRefValAnnotationWithoutName(Set<String> roots,
            ProcessingEnvironment processingEnv) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, RefVal.class);
        return createRefValAnnotationWithoutName(roots, builder);

    }

    public static AnnotationMirror createRefValAnnotation(String[] dataType,
            ProcessingEnvironment processingEnv) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, RefVal.class);
        builder.setValue("typeNames", dataType);
        return builder.build();
    }

    public static AnnotationMirror createRefValAnnotationWithRoots(Set<String> datatypes,
            Set<String> datatypesRoots, ProcessingEnvironment processingEnv) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, RefVal.class);
        return createRefValAnnotationWithRoots(datatypes, datatypesRoots, builder);
    }

    private static AnnotationMirror createRefValAnnotationWithRoots(final Set<String> datatypes,
            final Set<String> datatypesRoots, final AnnotationBuilder builder) {
        String[] refTypesInArray = new String[datatypes.size()];
        int i = 0;
        for (String datatype : datatypes) {
            refTypesInArray[i] = datatype.toString();
            i++;
        }

        String[] refTypesRootInArray = new String[datatypesRoots.size()];
        int j = 0;
        for (String refTypesRoot : datatypesRoots) {
            refTypesRootInArray[j] = refTypesRoot.toString();
            j++;
        }
        if (refTypesRootInArray.length > 0) {
            builder.setValue("typeNameRoots", refTypesRootInArray);
        }
        if (refTypesInArray.length > 0) {
            builder.setValue("typeNames", refTypesInArray);
        }

        return builder.build();
    }

    public static AnnotationMirror generateRefValAnnoFromNewClass(AnnotatedTypeMirror type,
            ProcessingEnvironment processingEnv) {
        TypeMirror tm = type.getUnderlyingType();
        String className = tm.toString();
        AnnotationMirror refValType = createRefValAnnotation(convert(className), processingEnv);
        return refValType;
    }

    public static AnnotationMirror generateRefValAnnoFromByteCode(AnnotatedTypeMirror type,
            ProcessingEnvironment processingEnv) {
        TypeMirror tm = type.getUnderlyingType();
        String className = tm.toString();
        AnnotationMirror refValType = createRefValAnnotationForByte(convert(className),
                processingEnv);
        return refValType;
    }

    public static AnnotationMirror generateRefValAnnoFromLiteral(AnnotatedTypeMirror type,
            ProcessingEnvironment processingEnv) {
        String refTypeInArray[] = convert(type.getUnderlyingType().toString());
        AnnotationMirror refValType = createRefValAnnotation(refTypeInArray, processingEnv);
        return refValType;
    }

    public static AnnotationMirror generateRefValAnnoFromLiteral(LiteralTree node,
            ProcessingEnvironment processingEnv) {
        String datatypeInArray[] = { "" };
        switch (node.getKind()) {
        case STRING_LITERAL:
            datatypeInArray = convert(String.class.toString().split(" ")[1]);
            break;
        case INT_LITERAL:
            datatypeInArray = convert(int.class.toString());
            break;
        case LONG_LITERAL:
            datatypeInArray = convert(long.class.toString());
            break;
        case FLOAT_LITERAL:
            datatypeInArray = convert(float.class.toString());
            break;
        case DOUBLE_LITERAL:
            datatypeInArray = convert(double.class.toString());
            break;
        case BOOLEAN_LITERAL:
            datatypeInArray = convert(boolean.class.toString());
            break;
        case CHAR_LITERAL:
            datatypeInArray = convert(char.class.toString());
            break;
        case NULL_LITERAL:
            // Null literal wouldn't be passed here.
            break;
        default:
            throw new BugInCF("Unknown literal tree: " + node.getKind().toString());
        }
        AnnotationMirror refValType = createRefValAnnotation(datatypeInArray, processingEnv);
        return refValType;
    }

    public static String[] convert(String... typeName) {
        return typeName;
    }

    public static AnnotationMirror createRefValAnnotation(String typeName,
            ProcessingEnvironment processingEnv) {
        Set<String> typeNames = new HashSet<String>();
        typeNames.add(typeName);
        AnnotationMirror am = RefValUtils.createRefValAnnotation(typeNames, processingEnv);
        return am;
    }
}
