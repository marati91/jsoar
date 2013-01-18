/*
 * Copyright (c) 2012 Dave Ray <daveray@gmail.com>
 *
 * Created on Mar 20, 2012
 */
package org.jsoar.kernel.epmem;

import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.epmem.DefaultEpisodicMemoryParams.Phase;
import org.jsoar.kernel.memory.WorkingMemory;
import org.jsoar.kernel.symbols.IdentifierImpl;

/**
 * @author voigtjr
 */
public interface EpisodicMemory
{
	/**
	 * episodic_memory.cpp:epmem_enabled
	 */
	boolean epmem_enabled();
	
    /**
     * episodic_memory.cpp:epmem_close
     * @throws SoarException
     */
    void epmem_close() throws SoarException;
    
    // TODO stub, not sure if these are the correct parameters
    void initializeNewContext(WorkingMemory wm, IdentifierImpl id);
    
    /**
     * Performs cleanup when a state is removed
     * 
     * <p>episodic_memory.h:epmem_reset
     */
    void epmem_reset(IdentifierImpl state);
    
    /**
     * The kernel calls this function to implement Soar-EpMem:
     * consider new storage and respond to any commands
     * 
     * <p>episodic_memory.h:epmem_go
     * 
     * @param allow_store
     */
    void epmem_go(boolean allow_store);
    
    Phase getPhase();
}