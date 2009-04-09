/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Sep 14, 2008
 */
package org.jsoar.kernel;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import org.jsoar.kernel.events.AbstractPhaseEvent;
import org.jsoar.kernel.events.AfterDecisionCycleEvent;
import org.jsoar.kernel.events.AfterElaborationEvent;
import org.jsoar.kernel.events.AfterHaltEvent;
import org.jsoar.kernel.events.BeforeDecisionCycleEvent;
import org.jsoar.kernel.events.BeforeElaborationEvent;
import org.jsoar.kernel.events.PhaseEvents;
import org.jsoar.kernel.events.RunLoopEvent;
import org.jsoar.kernel.io.InputOutputImpl;
import org.jsoar.kernel.learning.Chunker;
import org.jsoar.kernel.memory.WorkingMemory;
import org.jsoar.kernel.rhs.functions.RhsFunctionContext;
import org.jsoar.kernel.rhs.functions.RhsFunctionException;
import org.jsoar.kernel.rhs.functions.RhsFunctionHandler;
import org.jsoar.kernel.rhs.functions.StandaloneRhsFunctionHandler;
import org.jsoar.kernel.symbols.IdentifierImpl;
import org.jsoar.kernel.symbols.Symbol;
import org.jsoar.kernel.symbols.SymbolImpl;
import org.jsoar.kernel.tracing.Printer;
import org.jsoar.kernel.tracing.Trace;
import org.jsoar.kernel.tracing.TraceFormats;
import org.jsoar.kernel.tracing.Trace.Category;
import org.jsoar.util.Arguments;
import org.jsoar.util.adaptables.Adaptables;
import org.jsoar.util.properties.IntegerPropertyProvider;
import org.jsoar.util.properties.PropertyManager;
import org.jsoar.util.timing.ExecutionTimers;

/**
 * An attempt to encapsulate the Soar decision cycle
 * 
 * @author ray
 */
public class DecisionCycle
{
    private final Agent context;
    
    private InputOutputImpl io;
    private TraceFormats traceFormats;
    private Chunker chunker;
    private WorkingMemory workingMemory;
    private Consistency consistency;
    
    private static enum GoType
    {
        GO_PHASE, GO_ELABORATION, GO_DECISION,
        // unused: GO_STATE, GO_OPERATOR, GO_SLOT, GO_OUTPUT
    }
    
    private boolean system_halted = false;
    private boolean stop_soar = false;
    private String reason_for_stopping = null;
    
    /**
     * agent.h:324:current_phase
     * agent.cpp:153 (init)
     */
    public Phase current_phase = Phase.INPUT;
    /**
     * agent.h:349:go_type
     * agent.cpp:146 (init)
     */
    private GoType go_type = GoType.GO_DECISION;
    
    int e_cycles_this_d_cycle;
    private int run_phase_count;
    private int run_elaboration_count;
    private int pe_cycles_this_d_cycle;
    private int run_last_output_count;
    private int run_generated_output_count;
    public IntegerPropertyProvider d_cycle_count = new IntegerPropertyProvider(SoarProperties.D_CYCLE_COUNT);
    private IntegerPropertyProvider decision_phases_count = new IntegerPropertyProvider(SoarProperties.DECISION_PHASES_COUNT);
    private IntegerPropertyProvider e_cycle_count = new IntegerPropertyProvider(SoarProperties.E_CYCLE_COUNT);
    private IntegerPropertyProvider pe_cycle_count = new IntegerPropertyProvider(SoarProperties.PE_CYCLE_COUNT);
    public IntegerPropertyProvider inner_e_cycle_count = new IntegerPropertyProvider(SoarProperties.INNER_E_CYCLE_COUNT);
    
    /**
     * gsysparam.h:MAX_ELABORATIONS_SYSPARAM
     */
    private IntegerPropertyProvider maxElaborations = new IntegerPropertyProvider(SoarProperties.MAX_ELABORATIONS)
    {
        @Override
        public Integer set(Integer value)
        {
            Arguments.check(value > 0, "max elaborations must be greater than zero");
            return super.set(value);
        }
    };
    
    private boolean hitMaxElaborations = false;
    
    /**
     * gsysparams.h::MAX_NIL_OUTPUT_CYCLES_SYSPARAM
     */
    private int maxNilOutputCycles = 15;
    
    /**
     * rhsfun.cpp:199:halt_rhs_function_code
     */
    private final RhsFunctionHandler haltHandler = new StandaloneRhsFunctionHandler("halt") {

        @Override
        public SymbolImpl execute(RhsFunctionContext context, List<Symbol> arguments) throws RhsFunctionException
        {
            system_halted = true;
            
            // callback AFTER_HALT_SOAR_CALLBACK is fired from decision cycle
            
            return null;
        }};
    
    private final AfterHaltEvent afterHaltEvent;
    private final BeforeElaborationEvent beforeElaborationEvent;
    private final AfterElaborationEvent afterElaborationEvent;
    private final BeforeDecisionCycleEvent beforeDecisionCycleEvent;
    private final RunLoopEvent pollEvent;
    private final Map<Phase, AbstractPhaseEvent> beforePhaseEvents;
    private final Map<Phase, AbstractPhaseEvent> afterPhaseEvents;
    
    public DecisionCycle(Agent context)
    {
        this.context = context;

        this.afterHaltEvent = new AfterHaltEvent(context);
        this.beforeElaborationEvent = new BeforeElaborationEvent(context);
        this.afterElaborationEvent = new AfterElaborationEvent(context);
        this.beforeDecisionCycleEvent = new BeforeDecisionCycleEvent(context, Phase.INPUT);
        this.pollEvent = new RunLoopEvent(context);
        this.beforePhaseEvents = PhaseEvents.createBeforeEvents(context);
        this.afterPhaseEvents = PhaseEvents.createAfterEvents(context);
    }
    
    public void initialize()
    {
        final PropertyManager properties = this.context.getProperties();
        properties.setProvider(SoarProperties.D_CYCLE_COUNT, d_cycle_count);
        properties.setProvider(SoarProperties.E_CYCLE_COUNT, e_cycle_count);
        properties.setProvider(SoarProperties.DECISION_PHASES_COUNT, decision_phases_count);
        properties.setProvider(SoarProperties.PE_CYCLE_COUNT, pe_cycle_count);
        properties.setProvider(SoarProperties.INNER_E_CYCLE_COUNT, inner_e_cycle_count);
        properties.setProvider(SoarProperties.MAX_ELABORATIONS, maxElaborations);
        
        this.io = Adaptables.adapt(context, InputOutputImpl.class);
        this.traceFormats = Adaptables.adapt(context, TraceFormats.class);
        this.chunker = Adaptables.adapt(context, Chunker.class);
        this.workingMemory = Adaptables.adapt(context, WorkingMemory.class);
        this.consistency = Adaptables.adapt(context, Consistency.class);
        
        context.getRhsFunctions().registerHandler(haltHandler);
    }
    
    /**
     * Extracted from reinitialize_soar()
     *  
     * init_soar.cpp:410:reinitialize_soar
     */
    public void reset()
    {
        // Reinitialize the various halt and stop flags
        system_halted = false;
        stop_soar = false;
        reason_for_stopping = null;
        go_type = GoType.GO_DECISION;
        current_phase = Phase.INPUT;
        
        resetStatistics();
    }

    /**
     * init_soar.cpp:297:reset_statistics
     */
    private void resetStatistics()
    {
        d_cycle_count.reset();
        decision_phases_count.reset();
        e_cycle_count.reset();
        e_cycles_this_d_cycle = 0;
        pe_cycle_count.reset();
        pe_cycles_this_d_cycle = 0;
        run_phase_count = 0 ;
        run_elaboration_count = 0 ;
        run_last_output_count = 0 ;
        run_generated_output_count = 0 ;
        inner_e_cycle_count.reset();
    }
    
    /**
     * The current setting for max elaborations.
     * 
     * <p>Client code should access this value through the agent's
     * property manager.
     * 
     * gsysparam.h::MAX_ELABORATIONS_SYSPARAM
     * 
     * @return the maxElaborations
     */
    public int getMaxElaborations()
    {
        return maxElaborations.value.get();
    }

    /**
     * @return the hitMaxElaborations
     */
    public boolean isHitMaxElaborations()
    {
        return hitMaxElaborations;
    }

    /**
     * @param hitMaxElaborations the hitMaxElaborations to set
     */
    private void setHitMaxElaborations(boolean hitMaxElaborations)
    {
        this.hitMaxElaborations = hitMaxElaborations;
    }


    
    /**
     * runs Soar one top-level phases. Note that this does not start/stop the 
     * total_cpu_time timer--the caller must do this. 
     * 
     * <p>init_soar.cpp:474:do_one_top_level_phase
     */
    private void do_one_top_level_phase()
    {
        assert !system_halted;
        assert !stop_soar;
        
        ExecutionTimers.pause(context.getTotalKernelTimer());
        context.getEventManager().fireEvent(pollEvent);
        ExecutionTimers.start(context.getTotalKernelTimer());
        
        switch (current_phase)
        {
        case INPUT:       doInputPhase();         break;
        case PROPOSE:     doProposePhase();       break;
        case PREFERENCE:  doPreferencePhase();    break;
        case WM:          doWorkingMemoryPhase(); break;
        case APPLY:       doApplyPhase();         break; 
        case OUTPUT:      doOutputPhase();        break;
        case DECISION:    doDecisionPhase();      break;
        default: throw new IllegalStateException("Invalid phase enumeration value " + current_phase);
        }

        // update WM size statistics
        this.workingMemory.updateStats(context.getNumWmesInRete());

        checkForSystemHalt();

        if (stop_soar)
        {
            if (reason_for_stopping != null && reason_for_stopping.length() > 0)
            {
                context.getPrinter().print("\n%s", reason_for_stopping);
            }
        }
    }
    
    private void beforePhase(Phase phase)
    {
        context.getEventManager().fireEvent(beforePhaseEvents.get(phase));    
    }
    
    private void afterPhase(Phase phase)
    {
        this.run_phase_count++;
        
        context.getEventManager().fireEvent(afterPhaseEvents.get(phase));
    }
    
    private void beforeElaboration()
    {
        // FIXME return the correct enum top_level_phase constant in soar_call_data?
        context.getEventManager().fireEvent(beforeElaborationEvent);
    }
    
    private void afterElaboration()
    {
        // FIXME return the correct enum top_level_phase constant in soar_call_data?
        context.getEventManager().fireEvent(afterElaborationEvent);
    }
    
    /**
     * 
     */
    private void pauseTopLevelTimers()
    {
        ExecutionTimers.pause(context.getTotalKernelTimer());
        ExecutionTimers.pause(context.getTotalCpuTimer());
    }

    /**
     * 
     */
    private void startTopLevelTimers()
    {
        ExecutionTimers.start(context.getTotalCpuTimer());
        ExecutionTimers.start(context.getTotalKernelTimer());
    }
    
    /**
     * extracted from run_one_top_level_phase(), switch case DECISION_PHASE
     */
    private void doDecisionPhase()
    {
        assert current_phase == Phase.DECISION;
        
        /* not yet cleaned up for 8.6.0 release */

        final Trace trace = context.getTrace();
        Phase.DECISION.trace(trace, true);

        // #ifndef NO_TIMING_STUFF
        // start_timer (thisAgent, &thisAgent->start_phase_tv);
        // #endif
        
        this.decision_phases_count.increment(); // counts decisions, not cycles, for more accurate stats

        beforePhase(Phase.DECISION);
        
        context.decider.do_decision_phase(false);

        this.run_elaboration_count++; // All phases count as a run elaboration

        afterPhase(Phase.DECISION);

        if (trace.isEnabled() && trace.isEnabled(Category.CONTEXT_DECISIONS)) {
            final Writer writer = trace.getPrinter().getWriter();
            try
            {
                //writer.append("\n");
                traceFormats.print_lowest_slot_in_context_stack(writer, context.decider.bottom_goal);
                writer.append("\n");
                writer.flush();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
         }

        // reset elaboration counter
        this.e_cycles_this_d_cycle = 0;
        this.pe_cycles_this_d_cycle = 0;

        // Note: AGGRESSIVE_ONC used to be here. Dropped from jsoar because 
        // it didn't look like it had been used in years.
        Phase.DECISION.trace(trace, false);

        context.recMemory.FIRING_TYPE = SavedFiringType.PE_PRODS;
        current_phase = Phase.APPLY;

        //      #ifndef NO_TIMING_STUFF
        //      stop_timer (thisAgent, &thisAgent->start_phase_tv, 
        //          &thisAgent->decision_cycle_phase_timers[DECISION_PHASE]);
        //      #endif
    }

    /**
     * extracted from run_one_top_level_phase(), switch case OUTPUT_PHASE
     */
    private void doOutputPhase()
    {
        assert current_phase == Phase.OUTPUT;
        
        final Trace trace = context.getTrace();
        Phase.OUTPUT.trace(trace, true);

        // #ifndef NO_TIMING_STUFF
        // start_timer (thisAgent, &thisAgent->start_phase_tv);
        // #endif

        beforePhase(Phase.OUTPUT);
        io.do_output_cycle();

        // Count the outputs the agent generates (or times reaching max-nil-outputs without sending output)
        if (io.isOutputLinkChanged() || ((++(run_last_output_count)) >= maxNilOutputCycles))
        {
            this.run_last_output_count = 0;
            this.run_generated_output_count++;
        }

        this.run_elaboration_count++; // All phases count as a run elaboration
        
        afterPhase(Phase.OUTPUT);

        context.getEventManager().fireEvent(new AfterDecisionCycleEvent(context, Phase.OUTPUT));

        // #ifndef NO_TIMING_STUFF
        // stop_timer (thisAgent, &thisAgent->start_phase_tv,
        // &thisAgent->decision_cycle_phase_timers[OUTPUT_PHASE]);
        // #endif
        
        Phase.OUTPUT.trace(trace, false);
        current_phase = Phase.INPUT;
        this.d_cycle_count.increment();
    }

    /**
     * Checks whether max elaborations has been succeeded. If so, prints a
     * warning and sets the current phase to the nextPhase parameter and returns
     * true
     * 
     * @param nextPhase the phase to jump to if max elaborations is reached
     * @return true if max elaborations is reached. false otherwise
     */
    boolean checkForMaxElaborations(Phase nextPhase)
    {
        if (this.e_cycles_this_d_cycle >= maxElaborations.value.get() )
        {
            setHitMaxElaborations(true);
            context.getPrinter().warn("\nWarning: reached max-elaborations(%d); proceeding to %s phases.", maxElaborations.value.get(), nextPhase);
            // xml_generate_warning(thisAgent, "Warning: reached max-elaborations; proceeding to decision phases.");
            this.current_phase = nextPhase;
            return true;
        }
        return false;
    }
    
    /**
     * extracted from run_one_top_level_phase(), switch case APPLY_PHASE
     * 
     * <p>Modified at umich for new waterfall model, see:
     * https://winter.eecs.umich.edu/soarumwiki/index.php/Soar/Waterfall
     */
    private void doApplyPhase()
    {
        assert current_phase == Phase.APPLY;
        
        final Trace trace = context.getTrace();
        
        // added in 8.6 to clarify Soar8 decision cycle

        // #ifndef NO_TIMING_STUFF
        // start_timer (thisAgent, &thisAgent->start_phase_tv);
        // #endif

        /* e_cycle_count will always be zero UNLESS we are running by ELABORATIONS.
         * We only want to do the following if we've just finished DECISION and are
         * starting APPLY.  If this is the second elaboration for APPLY, then 
         * just do the while loop below.   KJC  June 05
         */
        if (this.e_cycles_this_d_cycle < 1)
        {
            Phase.APPLY.trace(trace, true);

            beforePhase(Phase.APPLY);

            // We need to generate this event here in case no elaborations fire...
            beforeElaboration();

            // 'prime' the cycle for a new round of production firings in the APPLY (pref/wm) phases
            this.consistency.initialize_consistency_calculations_for_new_decision();

            context.recMemory.FIRING_TYPE = SavedFiringType.PE_PRODS; // might get reset in det_high_active_prod_level...
            this.consistency.determine_highest_active_production_level_in_stack_apply();
            
            if (current_phase == Phase.OUTPUT)
            { 
                // no elaborations will fire this phase
                this.run_elaboration_count++; // All phases count as a run elaboration

                afterElaboration();
            }
        }
        
        // max-elaborations are checked in determine_highest_active... and if they
        // are reached, the current phases is set to OUTPUT.  phases is also set
        // to OUTPUT when APPLY is done.
        while (current_phase != Phase.OUTPUT)
        {
            if (this.e_cycles_this_d_cycle != 0)
            {
                // only for 2nd cycle or higher. 1st cycle fired above
                beforeElaboration();
            }
            
            context.recMemory.do_preference_phase(context.decider.top_goal);
            context.decider.do_working_memory_phase();

            // Update accounting
            this.e_cycle_count.increment();
            this.e_cycles_this_d_cycle++;
            this.run_elaboration_count++;

            if (context.recMemory.FIRING_TYPE == SavedFiringType.PE_PRODS)
            {
                this.pe_cycle_count.increment();
                this.pe_cycles_this_d_cycle++;
            }
            
            this.consistency.determine_highest_active_production_level_in_stack_apply();

            afterElaboration();

            if (this.go_type == GoType.GO_ELABORATION)
                break;
        }

        //  If we've finished APPLY, then current_phase will be equal to OUTPUT
        //  otherwise, we're only stopping because we're running by ELABORATIONS, so
        //  don't do the end-of-phases updating in that case.
        if (current_phase == Phase.OUTPUT)
        {
            /* This is a HACK for Soar 8.6.0 beta release... KCoulter April 05
             * We got here, because we should move to OUTPUT, so APPLY is done
             * Set phases back to APPLY, do print_phase, callbacks and reset phases to OUTPUT
             */
            current_phase = Phase.APPLY;
            Phase.APPLY.trace(trace, false);
            
            afterPhase(Phase.APPLY);

            current_phase = Phase.OUTPUT;
        }

        // #ifndef NO_TIMING_STUFF
        // stop_timer (thisAgent, &thisAgent->start_phase_tv, &thisAgent->decision_cycle_phase_timers[APPLY_PHASE]);
        // #endif
        
        // END of Soar8 APPLY PHASE
    }

    /**
     * extracted from do_one_top_level_phase(), switch case WM_PHASE
     */
    private void doWorkingMemoryPhase()
    {
        assert current_phase == Phase.WM;
        
        // starting with 8.6.0, WM_PHASE is only Soar 7 mode; see PROPOSE and APPLY
        // needs to be updated for gSKI interface, and gSKI needs to accommodate Soar 7

        /*  we need to tell gSKI WM Phase beginning... */

        // #ifndef NO_TIMING_STUFF
        // start_timer (thisAgent, &thisAgent->start_phase_tv);
        // #endif
        beforePhase(Phase.WM);
        context.decider.do_working_memory_phase();

        this.run_elaboration_count++; // All phases count as a run elaboration
        
        afterPhase(Phase.WM);

        current_phase = Phase.OUTPUT;

        // #ifndef NO_TIMING_STUFF
        // stop_timer (thisAgent, &thisAgent->start_phase_tv,
        // &thisAgent->decision_cycle_phase_timers[WM_PHASE]);
        // #endif

        afterElaboration();
        
        // END of Soar7 WM PHASE    
    }

    /**
     * extracted from do_one_top_level_phase(), switch case PROPOSE_PHASE
     */
    private void doProposePhase()
    {
        assert current_phase == Phase.PROPOSE;
        
        final Trace trace = context.getTrace();
        
        /* added in 8.6 to clarify Soar8 decision cycle */

        // #ifndef NO_TIMING_STUFF
        // start_timer (thisAgent, &thisAgent->start_phase_tv);
        // #endif

        /* e_cycles_this_d_cycle will always be zero UNLESS we are running by ELABORATIONS.
         * We only want to do the following if we've just finished INPUT and are
         * starting PROPOSE.  If this is the second elaboration for PROPOSE, then 
         * just do the while loop below.   KJC  June 05
         */
        if (this.e_cycles_this_d_cycle < 1)
        {
            Phase.PROPOSE.trace(trace, true);

            beforePhase(Phase.PROPOSE);

            // We need to generate this event here in case no elaborations fire...
            beforeElaboration();

            // 'Prime the decision for a new round of production firings at the end of
            this.consistency.initialize_consistency_calculations_for_new_decision();

            context.recMemory.FIRING_TYPE = SavedFiringType.IE_PRODS;
            this.consistency.determine_highest_active_production_level_in_stack_propose();

            if (current_phase == Phase.DECISION)
            { 
                // no elaborations will fire this phases
                this.run_elaboration_count++; // All phases count as a run elaboration

                afterElaboration();
            }
        }

        // max-elaborations are checked in determine_highest_active... and if they
        // are reached, the current phases is set to DECISION.  phases is also set
        // to DECISION when PROPOSE is done.
        while (current_phase != Phase.DECISION)
        {
            if (e_cycles_this_d_cycle != 0)
            {
                beforeElaboration();
            }
            
            context.recMemory.do_preference_phase(context.decider.top_goal);
            context.decider.do_working_memory_phase();
            
            // Update accounting.
            this.e_cycle_count.increment();
            this.e_cycles_this_d_cycle++;
            this.run_elaboration_count++;
            
            this.consistency.determine_highest_active_production_level_in_stack_propose();
            
            afterElaboration();
            
            if (this.go_type == GoType.GO_ELABORATION)
                break;
        }

        /*  If we've finished PROPOSE, then current_phase will be equal to DECISION
         *  otherwise, we're only stopping because we're running by ELABORATIONS, so
         *  don't do the end-of-phases updating in that case.
         */
        if (current_phase == Phase.DECISION)
        {
            /* This is a HACK for Soar 8.6.0 beta release... KCoulter April 05
             * We got here, because we should move to DECISION, so PROPOSE is done
             * Set phases back to PROPOSE, do print_phase, callbacks, and then
             * reset phases to DECISION
             */
            this.current_phase = Phase.PROPOSE;
            Phase.PROPOSE.trace(trace, false);

            afterPhase(Phase.PROPOSE);
            this.current_phase = Phase.DECISION;
        }

        // #ifndef NO_TIMING_STUFF
        // stop_timer (thisAgent, &thisAgent->start_phase_tv, &thisAgent->decision_cycle_phase_timers[PROPOSE_PHASE]);
        // #endif

        // END of Soar8 PROPOSE PHASE    
    }

    /**
     * extracted from do_one_top_level_phase(), switch case INPUT_PHASE
     */
    private void doInputPhase()
    {
        assert current_phase == Phase.INPUT;
        
        final Trace trace = context.getTrace();
        Phase.INPUT.trace(trace, true);

        // for Operand2 mode using the new decision cycle ordering,
        // we need to do some initialization in the INPUT PHASE, which
        // now comes first.  e_cycles are also zeroed before the APPLY Phase.
        this.chunker.chunks_this_d_cycle = 0;
        this.e_cycles_this_d_cycle = 0;
        
        // #ifndef NO_TIMING_STUFF
        // start_timer (thisAgent, &thisAgent->start_phase_tv);
        // #endif

        // we check e_cycle_count because Soar 7 runs multiple input cycles per decision
        // always true for Soar 8
        if (e_cycles_this_d_cycle == 0)
        {
            context.getEventManager().fireEvent(beforeDecisionCycleEvent);
        }

        // #ifdef REAL_TIME_BEHAVIOR /* RM Jones */
        // test_for_input_delay(thisAgent);
        // #endif
        // #ifdef ATTENTION_LAPSE /* RM Jones */
        // determine_lapsing(thisAgent);
        // #endif

        beforePhase(Phase.INPUT);

        io.do_input_cycle();

        run_elaboration_count++; // All phases count as a run elaboration
        
        afterPhase(Phase.INPUT);
        
        Phase.INPUT.trace(trace, false);

        // #ifndef NO_TIMING_STUFF /* REW: 28.07.96 */
        // stop_timer (thisAgent, &thisAgent->start_phase_tv, &thisAgent->decision_cycle_phase_timers[INPUT_PHASE]);
        // #endif

        current_phase = Phase.PROPOSE;
    }

    /**
     * extracted from do_one_top_level_phase, switch case PREFERENCE_PHASE
     */
    private void doPreferencePhase()
    {
        assert current_phase == Phase.PREFERENCE;
        
        // starting with 8.6.0, PREFERENCE_PHASE is only Soar 7 mode -- applyPhase not valid here
        // needs to be updated for gSKI interface, and gSKI needs to accommodate Soar 7

        beforeElaboration();

        // #ifndef NO_TIMING_STUFF
        // start_timer (thisAgent, &thisAgent->start_phase_tv);
        // #endif
        beforePhase(Phase.PREFERENCE);
        context.recMemory.do_preference_phase(context.decider.top_goal);

        this.run_elaboration_count++; // All phases count as a run elaboration
        
        afterPhase(Phase.PREFERENCE);

        current_phase = Phase.WM;

        // #ifndef NO_TIMING_STUFF
        // stop_timer (thisAgent, &thisAgent->start_phase_tv, &thisAgent->decision_cycle_phase_timers[PREFERENCE_PHASE]);
        // #endif
        
        // END of Soar7 PREFERENCE PHASE
    }

    /**
     * At the beginning of a top level phase, we first check whether the agent 
     * is halted and immediately bail out if it is, printing out a halted
     * message.
     * 
     * @return true if the system is halted, false otherwise
     */
    private boolean checkForSystemHaltedAtStartOfTopLevel()
    {
        final Printer printer = context.getPrinter();

        if (system_halted)
        {
            printer.print("\nSystem halted.  Use (init-soar) before running Soar again.");
            printer.flush();
            stop_soar = true;
            reason_for_stopping = "System halted.";
            return true;
        }
        return false;
    }

    /**
     * At the end of a top level phase, we check whether the system has been
     * halted, and if so, fire the halt event and do some cleanup.
     */
    private void checkForSystemHalt()
    {
        if (system_halted)
        {
            stop_soar = true;
            reason_for_stopping = "System halted.";
            
            context.getEventManager().fireEvent(afterHaltEvent);

            // To model episodic task, after halt, perform RL update with next-state value 0
            if (context.rl.rl_enabled())
            {
                // TODO how about a method?
                for (IdentifierImpl g = context.decider.bottom_goal; g != null; g = g.higher_goal)
                {
                    context.rl.rl_tabulate_reward_value_for_goal(g);
                    context.rl.rl_perform_update(0, g);
                }
            }
        }
    }

    /**
     * init_soar.cpp:1123:run_for_n_phases
     * 
     * @param n Number of phases to run. Must be non-negative
     * @throws IllegalArgumentException if n is negative
     */
    private void run_for_n_phases(int n)
    {
        Arguments.check(n >= 0, "n must be non-negative");
        
        startTopLevelTimers();
        
        stop_soar = false;
        reason_for_stopping = null;
        setHitMaxElaborations(false);
        while (!stop_soar && n != 0)
        {
            do_one_top_level_phase();
            n--;
        }
        
        pauseTopLevelTimers();
    }

    /**
     * Run for n elaboration cycles
     * 
     * init_soar.cpp:1142:run_for_n_elaboration_cycles
     * 
     * @param n Number of elaboration cycles to run. Must be non-negative
     * @throws IllegalArgumentException if n is negative
     */
    private void run_for_n_elaboration_cycles(int n)
    {
        Arguments.check(n >= 0, "n must be non-negative");

        startTopLevelTimers();

        stop_soar = false;
        reason_for_stopping = null;
        int d_cycles_at_start = d_cycle_count.value.get();
        int elapsed_cycles = 0;
        GoType save_go_type = GoType.GO_PHASE;
        
        elapsed_cycles = -1;
        save_go_type = go_type;
        go_type = GoType.GO_ELABORATION;
        // need next line or runs only the input phases for "d 1" after init-soar
        if (d_cycles_at_start == 0)
            d_cycles_at_start++;
        
        while (!stop_soar)
        {
            elapsed_cycles++;
            if (n == elapsed_cycles)
                break;
            do_one_top_level_phase();
        }
        go_type = save_go_type;

        pauseTopLevelTimers();
    }

    /**
     * init_soar.cpp:1181:run_for_n_modifications_of_output
     * 
     * @param n Number of modifications. Must be non-negative.
     * @throws IllegalArgumentException if n is negative
     */
    private void run_for_n_modifications_of_output(int n)
    {
        Arguments.check(n >= 0, "n must be non-negative");

        startTopLevelTimers();

        stop_soar = false;
        reason_for_stopping = null;
        int count = 0;
        while (!stop_soar && n != 0)
        {
            boolean was_output_phase = current_phase == Phase.OUTPUT;
            do_one_top_level_phase();
            if (was_output_phase)
            {
                if (io.isOutputLinkChanged())
                {
                    n--;
                }
                else
                {
                    count++;
                }
            }
            if (count >= this.maxNilOutputCycles)
            {
                stop_soar = true;
                reason_for_stopping = "exceeded max_nil_output_cycles with no output";
            }
        }
        
        pauseTopLevelTimers();
    }

    /**
     * Run for n decision cycles
     * 
     * @param n Number of cycles to run. Must be non-negative
     * @throws IllegalArgumentException if n is negative
     */
    private void run_for_n_decision_cycles(int n)
    {
        Arguments.check(n >= 0, "n must be non-negative");

        startTopLevelTimers();

        stop_soar = false;
        reason_for_stopping = null;
        int d_cycles_at_start = d_cycle_count.value.get();
        /* need next line or runs only the input phases for "d 1" after init-soar */
        if (d_cycles_at_start == 0)
            d_cycles_at_start++;
        while (!stop_soar)
        {
            if (n == (d_cycle_count.value.get() - d_cycles_at_start))
                break;
            do_one_top_level_phase();
        }
        
        pauseTopLevelTimers();
    }
    
    /**
     * Client code should use {@link Agent#runFor(int, RunType)}
     * 
     * @param n
     * @param runType
     */
    public void runFor(int n, RunType runType)
    {
        if(checkForSystemHaltedAtStartOfTopLevel())
        {
            return;
        }
        switch(runType)
        {
        case ELABORATIONS: run_for_n_elaboration_cycles(n); break;
        case DECISIONS: run_for_n_decision_cycles(n); break;
        case PHASES: run_for_n_phases(n); break;
        case MODIFICATIONS_OF_OUTPUT: run_for_n_modifications_of_output(n); break;
        case FOREVER: runForever(); break;
        default:
            throw new IllegalArgumentException("Unknown run type: " + runType);
        }
    }
    
    /**
     * Client code should use {@link Agent#runForever()}
     * 
     * <p>init_soar.cpp:1105:run_forever
     */
    public void runForever()
    {
        if(checkForSystemHaltedAtStartOfTopLevel())
        {
            return;
        }
        startTopLevelTimers();
        
        stop_soar = false;
        reason_for_stopping = null;
        while (!stop_soar)
        {
            do_one_top_level_phase();
        }
        
        pauseTopLevelTimers();
    }
    
        
    /**
     * @return true if this agent has been stopped, halted, or interrupted
     */
    public boolean isStopped()
    {
        return stop_soar;
    }
    
    /**
     * @return The reason for stopping, or <code>null</code> if {@link #isStopped()}
     *      is false.
     */
    public String getReasonForStop()
    {
        return reason_for_stopping;
    }
    
    /**
     * Stop the agent.
     */
    public void stop()
    {
        if(!stop_soar)
        {
            this.stop_soar = true;
            this.reason_for_stopping = "Stopped by user.";
        }
    }
    
    /**
     * Interrupt this agent.
     * 
     * <p>rhsfun.cpp:231
     * 
     * @param production The name of the production causing the interrupt, or 
     *  <code>null</code> if unknown.
     */
    public void interrupt(String production)
    {
        this.stop_soar = true;
        this.reason_for_stopping = "*** Interrupt from production " + production + " ***";
    }

    /**
     * @return true if the agent is halted
     */
    public boolean isHalted()
    {
        return system_halted;
    }
    
    /**
     * Halt this agent. A halted agent can only be restarted with an init-soar.
     * 
     * @param string Reason for the halt
     */
    public void halt(String string)
    {
        stop_soar = true;
        system_halted = true;
        reason_for_stopping = "Max Goal Depth exceeded.";
    }
}