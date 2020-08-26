package refval;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.LiteralTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.PrimitiveType;

import checkers.inference.InferenceAnnotatedTypeFactory;
import checkers.inference.InferenceChecker;
import checkers.inference.InferenceMain;
import checkers.inference.InferrableChecker;
import checkers.inference.SlotManager;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.ConstraintManager;
import refval.util.RefValUtils;

/**
 * RefValInferenceAnnotatedTypeFactory handles boxing and unboxing for
 * primitive types. The RefVal type should always same as declared type for
 * both cases.
 * 
 * @author jianchu
 *
 */
public class RefValInferenceAnnotatedTypeFactory extends InferenceAnnotatedTypeFactory {

    public RefValInferenceAnnotatedTypeFactory(InferenceChecker inferenceChecker,
            boolean withCombineConstraints, BaseAnnotatedTypeFactory realTypeFactory,
            InferrableChecker realChecker, SlotManager slotManager, ConstraintManager constraintManager) {
        super(inferenceChecker, withCombineConstraints, realTypeFactory, realChecker, slotManager,
                constraintManager);
        postInit();
    }

    @Override
    public TreeAnnotator createTreeAnnotator() {
        return new ListTreeAnnotator(new LiteralTreeAnnotator(this),
                new RefValInferenceTreeAnnotator(this, realChecker, realTypeFactory,
                        variableAnnotator, slotManager));
    }

    @Override
    public AnnotatedDeclaredType getBoxedType(AnnotatedPrimitiveType type) {
        TypeElement typeElt = types.boxedClass(type.getUnderlyingType());
        AnnotationMirror am = RefValUtils.createRefValAnnotation(typeElt.asType().toString(),
                this.processingEnv);
        AnnotatedDeclaredType dt = fromElement(typeElt);
        ConstantSlot cs = slotManager.createConstantSlot(am);
        dt.addAnnotation(slotManager.getAnnotation(cs));
        dt.addAnnotation(cs.getValue());
        return dt;
    }

    @Override
    public AnnotatedPrimitiveType getUnboxedType(AnnotatedDeclaredType type)
            throws IllegalArgumentException {
        PrimitiveType primitiveType = types.unboxedType(type.getUnderlyingType());
        AnnotationMirror am = RefValUtils.createRefValAnnotation(primitiveType.toString(),
                this.processingEnv);
        AnnotatedPrimitiveType pt = (AnnotatedPrimitiveType) AnnotatedTypeMirror.createType(
                primitiveType, this, false);
        ConstantSlot cs = slotManager.createConstantSlot(am);
        pt.addAnnotation(slotManager.getAnnotation(cs));
        pt.addAnnotation(cs.getValue());
        return pt;
    }
}
