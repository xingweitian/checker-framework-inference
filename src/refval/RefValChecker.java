package refval;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;

import checkers.inference.BaseInferrableChecker;
import checkers.inference.InferenceChecker;
import checkers.inference.InferrableChecker;
import checkers.inference.SlotManager;
import checkers.inference.model.ConstraintManager;

/**
 * Checker for RefVal type system.
 * 
 * @author jianchu
 *
 */
public class RefValChecker extends BaseInferrableChecker {

    @Override
    public RefValVisitor createVisitor(InferenceChecker ichecker, BaseAnnotatedTypeFactory factory,
            boolean infer) {
        return new RefValVisitor(this, ichecker, factory, infer);
    }

    @Override
    public RefValAnnotatedTypeFactory createRealTypeFactory() {
        return new RefValAnnotatedTypeFactory(this);
    }

    @Override
    public RefValInferenceAnnotatedTypeFactory createInferenceATF(InferenceChecker inferenceChecker,
            InferrableChecker realChecker, BaseAnnotatedTypeFactory realTypeFactory,
            SlotManager slotManager, ConstraintManager constraintManager) {
        return new RefValInferenceAnnotatedTypeFactory(
                inferenceChecker, realChecker.withCombineConstraints(), realTypeFactory, realChecker,
                slotManager, constraintManager);
    }
}
