/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 1.3.35
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package sml;

public enum smlWorkingMemoryEventId {
  smlEVENT_OUTPUT_PHASE_CALLBACK(smlAgentEventId.smlEVENT_LAST_AGENT_EVENT.swigValue() + 1),
  smlEVENT_INPUT_PHASE_CALLBACK,
  smlEVENT_LAST_WM_EVENT(smlEVENT_OUTPUT_PHASE_CALLBACK);

  public final int swigValue() {
    return swigValue;
  }

  public static smlWorkingMemoryEventId swigToEnum(int swigValue) {
    smlWorkingMemoryEventId[] swigValues = smlWorkingMemoryEventId.class.getEnumConstants();
    if (swigValue < swigValues.length && swigValue >= 0 && swigValues[swigValue].swigValue == swigValue)
      return swigValues[swigValue];
    for (smlWorkingMemoryEventId swigEnum : swigValues)
      if (swigEnum.swigValue == swigValue)
        return swigEnum;
    throw new IllegalArgumentException("No enum " + smlWorkingMemoryEventId.class + " with value " + swigValue);
  }

  private smlWorkingMemoryEventId() {
    this.swigValue = SwigNext.next++;
  }

  private smlWorkingMemoryEventId(int swigValue) {
    this.swigValue = swigValue;
    SwigNext.next = swigValue+1;
  }

  private smlWorkingMemoryEventId(smlWorkingMemoryEventId swigEnum) {
    this.swigValue = swigEnum.swigValue;
    SwigNext.next = this.swigValue+1;
  }

  private final int swigValue;

  private static class SwigNext {
    private static int next = 0;
  }
}

