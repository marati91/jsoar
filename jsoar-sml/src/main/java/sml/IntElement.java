/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 1.3.35
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package sml;

import org.jsoar.kernel.memory.Wme;

public class IntElement extends WMElement {

    IntElement(Agent agent, IdentifierSymbol parentSymbol, Wme wme)
    {
        super(agent, parentSymbol, wme);
    }
    
    IntElement(Agent agent, Identifier parent, Wme wme)
    {
        super(agent, parent.GetSymbol(), wme);
    }

public synchronized void delete() {
    super.delete();
  }

  public String GetValueType() {
      return sml_Names.getKTypeInt();
  }

  public int GetValue() {
      // TODO SML int to long
      return (int) wme.getValue().asInteger().getValue();
  }

  public IntElement ConvertToIntElement() {
      return this;
  }
  
}
