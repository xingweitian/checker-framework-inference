package dataflow;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.javacutil.AnnotationBuilder;

import checkers.inference.BaseInferrableChecker;
import checkers.inference.InferenceChecker;
import checkers.inference.InferrableChecker;
import checkers.inference.SlotManager;
import checkers.inference.model.ConstraintManager;
import dataflow.qual.RefVal;
import dataflow.qual.UnknownRefVal;

/**
 * Checker for Dataflow type system.
 * 
 * @author jianchu
 *
 */
public class DataflowChecker extends BaseInferrableChecker {
    public AnnotationMirror REFVAL, UNKNOWNREFVAL;

    @Override
    public void initChecker() {
        super.initChecker();
        setAnnotations();
    }

    protected void setAnnotations() {
        final Elements elements = processingEnv.getElementUtils();
        REFVAL = AnnotationBuilder.fromClass(elements, RefVal.class);
        UNKNOWNREFVAL = AnnotationBuilder.fromClass(elements, UnknownRefVal.class);
    }

    @Override
    public DataflowVisitor createVisitor(InferenceChecker ichecker, BaseAnnotatedTypeFactory factory,
            boolean infer) {
        return new DataflowVisitor(this, ichecker, factory, infer);
    }

    @Override
    public DataflowAnnotatedTypeFactory createRealTypeFactory() {
        return new DataflowAnnotatedTypeFactory(this);
    }

    @Override
    public DataflowInferenceAnnotatedTypeFactory createInferenceATF(InferenceChecker inferenceChecker,
            InferrableChecker realChecker, BaseAnnotatedTypeFactory realTypeFactory,
            SlotManager slotManager, ConstraintManager constraintManager) {
        DataflowInferenceAnnotatedTypeFactory dataflowInferenceTypeFactory = new DataflowInferenceAnnotatedTypeFactory(
                inferenceChecker, realChecker.withCombineConstraints(), realTypeFactory, realChecker,
                slotManager, constraintManager);
        return dataflowInferenceTypeFactory;
    }
}