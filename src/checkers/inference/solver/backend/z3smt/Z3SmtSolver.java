package checkers.inference.solver.backend.z3smt;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.model.serialization.ToStringSerializer;
import checkers.inference.solver.backend.Solver;
import checkers.inference.solver.backend.z3smt.encoder.Z3SmtSoftConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.ExternalSolverUtils;
import checkers.inference.solver.util.FileUtils;
import checkers.inference.solver.util.SolverArg;
import checkers.inference.solver.util.SolverEnvironment;
import checkers.inference.solver.util.Statistics;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Optimize;

import org.checkerframework.javacutil.BugInCF;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.lang.model.element.AnnotationMirror;

/** Z3SmtSolver uses microsoft's Z3 as its underlying solver. */
public class Z3SmtSolver<SlotEncodingT, SlotSolutionT>
        extends Solver<Z3SmtFormatTranslator<SlotEncodingT, SlotSolutionT>> {

    /** Z3SmtSolver's arguments. */
    public enum Z3SolverEngineArg implements SolverArg {
        /** Use annotation mode and soft constraints. */
        optimizingMode
    }

    /** The main interaction with Z3 happens via Context. */
    protected final Context ctx;
    /** Object for managing optimization context. */
    protected Optimize opt;
    /** The logger. */
    private static final Logger logger = Logger.getLogger(Z3SmtSolver.class.getName());
    /** The contents to write into a SMT file. */
    protected StringBuilder smtFileContents;
    /** The Z3 command to run. */
    protected static final String z3Program = "z3";
    /** True if optimizing mode is using, else False. */
    protected boolean optimizingMode;
    /** Whether get unsatisfiable constraints or not. */
    protected boolean getUnsatCore;
    /**
     * When {@link #optimizingMode} and {@link #getUnsatCore} is True, this field stores the
     * serialized constraints, only used with unsat-core to find unsat constraints by giving the
     * constraint IDs.
     */
    protected final Map<String, Constraint> serializedConstraints = new HashMap<>();
    /** Contains all the unsat constraint IDs. */
    protected final List<String> unsatConstraintIDs = new ArrayList<>();
    /** The project's root folder. */
    protected static final File pathToProject = new File("").getAbsoluteFile();
    /** The file contains constraints, is stored under the project's root folder. */
    protected static final File constraintsFile = new File(pathToProject, "z3Constraints.smt");
    /** The file contains unsatisfiable constraints, is stored under the project's root folder. */
    protected static final File constraintsUnsatCoreFile =  new File(pathToProject + "z3ConstraintsUnsatCore.smt");
    /** The file contains statistics information of constraints, is stored under the project's root folder. */
    protected static final File constraintsStatsFile = new File(pathToProject + "z3ConstraintsGlob.smt");
    /** The start time of serialization. */
    protected long serializationStart;
    /** The end time of serialization. */
    protected long serializationEnd;
    /** The start time of solving. */
    protected long solvingStart;
    /** The end time of solving. */
    protected long solvingEnd;

    public Z3SmtSolver(
            SolverEnvironment solverEnvironment,
            Collection<Slot> slots,
            Collection<Constraint> constraints,
            Z3SmtFormatTranslator<SlotEncodingT, SlotSolutionT> z3SmtFormatTranslator,
            Lattice lattice) {
        super(solverEnvironment, slots, constraints, z3SmtFormatTranslator, lattice);
        Map<String, String> z3Args = new HashMap<>();
        int timeout = timeout();
        if (timeout != -1) {
            z3Args.put("timeout", Integer.toString(timeout));
        }
        ctx = new Context(z3Args);
        z3SmtFormatTranslator.init(ctx);
    }

    /**
     * Returns the timeout for Z3 solver. 2 minutes is the default value. -1 indicates no timeout.
     *
     * @return the timeout for Z3 solver
     */
    protected int timeout() {
        return 2 * 60 * 1000;
    }

    // Main entry point
    @Override
    public Map<Integer, AnnotationMirror> solve() {
        optimizingMode = solverEnvironment.getBoolArg(Z3SolverEngineArg.optimizingMode);
        getUnsatCore = false;
        if (optimizingMode) {
            logger.info("Encoding for optimizing mode");
        } else {
            logger.info("Encoding for non-optimizing mode");
        }
        serializeSMTFileContents();
        solvingStart = System.currentTimeMillis();
        List<String> results = runZ3Solver();
        solvingEnd = System.currentTimeMillis();
        Statistics.addOrIncrementEntry(
                "smt_serialization_time(millisec)", serializationEnd - serializationStart);
        Statistics.addOrIncrementEntry("smt_solving_time(millisec)", solvingEnd - solvingStart);
        if (results == null) {
            logger.warning("!!! The set of constraints is unsatisfiable! !!!");
            return new HashMap<>();
        }
        return formatTranslator.decodeSolution(
                        results, solverEnvironment.processingEnvironment);
    }

    @Override
    public Collection<Constraint> explainUnsatisfiable() {
        optimizingMode = false;
        getUnsatCore = true;
        logger.info("Now encoding for unsat core dump.");
        serializeSMTFileContents();
        solvingStart = System.currentTimeMillis();
        runZ3Solver();
        solvingEnd = System.currentTimeMillis();
        Statistics.addOrIncrementEntry(
                "smt_unsat_serialization_time(millisec)", serializationEnd - serializationStart);
        Statistics.addOrIncrementEntry(
                "smt_unsat_solving_time(millisec)", solvingEnd - solvingStart);
        List<Constraint> unsatConstraints = new ArrayList<>();
        for (String constraintID : unsatConstraintIDs) {
            Constraint c = serializedConstraints.get(constraintID);
            unsatConstraints.add(c);
        }
        return unsatConstraints;
    }

    /** Encodes constraints to a smt file. */
    private void serializeSMTFileContents() {
        // Create a fresh optimize context
        opt = ctx.mkOptimize();
        smtFileContents = new StringBuilder();
        if (!optimizingMode && getUnsatCore) {
            smtFileContents.append("(set-option :produce-unsat-cores true)\n");
        }
        serializationStart = System.currentTimeMillis();
        encodeAllSlots();
        encodeAllConstraints();
        if (optimizingMode) {
            encodeAllSoftConstraints();
        }
        serializationEnd = System.currentTimeMillis();
        logger.info("Encoding constraints done");
        smtFileContents.append("(check-sat)\n");
        if (!optimizingMode && getUnsatCore) {
            smtFileContents.append("(get-unsat-core)\n");
        } else {
            smtFileContents.append("(get-model)\n");
        }
        logger.info("Writing constraints to file: " + constraintsFile.getAbsolutePath());
        writeConstraintsToFile();
    }

    /** Writes the constraints to a smt file and a constraints stats file. */
    private void writeConstraintsToFile() {
        String fileContents = smtFileContents.toString();
        if (!getUnsatCore) {
            // Write the constraints to the file for external solver use
            FileUtils.writeFile(constraintsFile, fileContents);
        } else {
            // Write the constraints to the file for external solver use
            FileUtils.writeFile(constraintsUnsatCoreFile, fileContents);
        }
        // Write a copy in append mode to stats file for later bulk analysis
        FileUtils.appendFile(constraintsStatsFile, fileContents);
    }

    /** Encodes and generates the constraints for all the slots. */
    protected void encodeAllSlots() {
        // Preprocess slots
        formatTranslator.preAnalyzeSlots(slots);
        // Generate slot constraints
        for (Slot slot : slots) {
            if (slot.isVariable()) {
                BoolExpr wfConstraint = formatTranslator.encodeSlotWellFormedConstraint(slot);
                if (!wfConstraint.simplify().isTrue()) {
                    opt.Assert(wfConstraint);
                }
                if (optimizingMode) {
                    encodeSlotPreferenceConstraint(slot);
                }
            }
        }
        String slotDefinitionsAndConstraints = opt.toString();
        int truncateIndex = slotDefinitionsAndConstraints.lastIndexOf("(check-sat)");
        assert truncateIndex != -1;
        // {opt.toString()} includes "(check-sat)" as the last line,
        // since the encoding is not finished yet, don't add this line to smt file for now.
        smtFileContents.append(slotDefinitionsAndConstraints, 0, truncateIndex);
    }

    @Override
    protected void encodeAllConstraints() {
        int current = 1;
        StringBuilder constraintSmtFileContents = new StringBuilder();
        for (Constraint constraint : constraints) {
            BoolExpr serializedConstraint = constraint.serialize(formatTranslator);
            if (serializedConstraint == null) {
                // TODO: Should error abort if unsupported constraint detected.
                // Currently warning is a workaround for making ontology
                // working, as in some cases existential constraints generated.
                // Should investigate on this, and change this to ErrorAbort
                // when eliminated unsupported constraints.
                logger.warning(
                        "Unsupported constraint detected! Constraint type: "
                                + constraint.getClass().getSimpleName());
                continue;
            }
            Expr simplifiedConstraint = serializedConstraint.simplify();
            if (simplifiedConstraint.isTrue()) {
                // This only works if the BoolExpr is directly the value Z3True.
                // Still a good filter, but doesn't filter enough.
                // EG: (and (= false false) (= false false) (= 0 0) (= 0 0) (= 0 0))
                // Skip tautology.
                current++;
                continue;
            }
            if (simplifiedConstraint.isFalse()) {
                final ToStringSerializer toStringSerializer = new ToStringSerializer(false);
                throw new BugInCF(
                        "impossible constraint: "
                                + constraint.serialize(toStringSerializer)
                                + "\nSerialized:\n"
                                + serializedConstraint);
            }
            String clause = simplifiedConstraint.toString();
            if (!optimizingMode && getUnsatCore) {
                // Add assertions with names, for unsat core dump
                String constraintName = constraint.getClass().getSimpleName() + current;
                constraintSmtFileContents.append("(assert (! ");
                constraintSmtFileContents.append(clause);
                constraintSmtFileContents.append(" :named ").append(constraintName).append("))\n");
                // Add constraint to serialized constraints map, so that we can
                // retrieve later using the constraint name when outputting the unsat core
                serializedConstraints.put(constraintName, constraint);
            } else {
                constraintSmtFileContents.append("(assert ");
                constraintSmtFileContents.append(clause);
                constraintSmtFileContents.append(")\n");
            }
            current++;
        }
        smtFileContents.append(constraintSmtFileContents);
    }

    /** Encodes all the soft constraints. */
    protected void encodeAllSoftConstraints() {
        final Z3SmtSoftConstraintEncoder<SlotEncodingT, SlotSolutionT> encoder = formatTranslator
            .createSoftConstraintEncoder();
        smtFileContents.append(encoder.encodeAndGetSoftConstraints(constraints));
    }

    /** Encodes soft preference constraints. */
    protected void encodeSlotPreferenceConstraint(Slot varSlot) {
        // Empty string means no optimization group
        opt.AssertSoft(
                formatTranslator.encodeSlotPreferenceConstraint(varSlot), 1, "");
    }

    /** Executes the z3 command to solve constraints. */
    private List<String> runZ3Solver() {
        // TODO: add z3 stats?
        String[] command;
        if (!getUnsatCore) {
            command = new String[]{z3Program, constraintsFile.getAbsolutePath()};
        } else {
            command = new String[]{z3Program, constraintsUnsatCoreFile.getAbsolutePath()};
        }
        // Stores results from z3 program output
        final List<String> results = new ArrayList<>();
        // Run command
        // TODO: check that stdErr has no errors
        int exitStatus =
                ExternalSolverUtils.runExternalSolver(
                        command,
                        stdOut -> parseStdOut(stdOut, results),
                        stdErr -> ExternalSolverUtils.printStdStream(System.err, stdErr));
        // If exit status from z3 is not 0, then it is unsat
        return exitStatus == 0 ? results : null;
    }

    /** Parses the std output from the z3 process and handles sat and unsat outputs. */
    private void parseStdOut(BufferedReader stdOut, List<String> results) {
        String line;
        boolean declarationLine = true;
        boolean unsat = false;
        try {
            while ((line = stdOut.readLine()) != null) {
                // Each result line is "varName value"
                StringBuilder resultsLine = new StringBuilder();
                line = line.trim();
                if (getUnsatCore) {
                    // UNSAT cases
                    if (line.contentEquals("unsat")) {
                        unsat = true;
                        continue;
                    }
                    if (unsat) {
                        if (line.startsWith("(")) {
                            line = line.substring(1); // remove open bracket
                        }
                        if (line.endsWith(")")) {
                            line = line.substring(0, line.length() - 1);
                        }
                        Collections.addAll(unsatConstraintIDs, line.split(" "));
                    }
                } else {
                    // SAT cases
                    // Processing define-fun lines
                    if (declarationLine && line.startsWith("(define-fun")) {
                        declarationLine = false;
                        int firstBar = line.indexOf('|');
                        int lastBar = line.lastIndexOf('|');
                        assert firstBar != -1;
                        assert lastBar != -1;
                        assert firstBar < lastBar;
                        assert line.contains("Bool") || line.contains("Int");
                        // Copy z3 variable name into results line
                        resultsLine.append(line, firstBar + 1, lastBar);
                        continue;
                    }
                    // Processing lines immediately following define-fun lines
                    if (!declarationLine) {
                        declarationLine = true;
                        String value = line.substring(0, line.lastIndexOf(')'));
                        if (value.contains("-")) { // negative number
                            // Remove brackets surrounding negative numbers
                            value = value.substring(1, value.length() - 1);
                            // Remove space between - and the number itself
                            value = String.join("", value.split(" "));
                        }
                        resultsLine.append(" ").append(value);
                        results.add(resultsLine.toString());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
