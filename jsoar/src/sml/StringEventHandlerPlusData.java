/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 1.3.35
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package sml;

public class StringEventHandlerPlusData extends EventHandlerPlusData {
  private long swigCPtr;

  protected StringEventHandlerPlusData(long cPtr, boolean cMemoryOwn) {
    super(smlJNI.SWIGStringEventHandlerPlusDataUpcast(cPtr), cMemoryOwn);
    swigCPtr = cPtr;
  }

  protected static long getCPtr(StringEventHandlerPlusData obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if(swigCPtr != 0 && swigCMemOwn) {
      swigCMemOwn = false;
      smlJNI.delete_StringEventHandlerPlusData(swigCPtr);
    }
    swigCPtr = 0;
    super.delete();
  }

  public void setM_Handler(SWIGTYPE_p_f_sml__smlStringEventId_p_void_p_sml__Kernel_p_q_const__char__std__string value) {
    smlJNI.StringEventHandlerPlusData_m_Handler_set(swigCPtr, this, SWIGTYPE_p_f_sml__smlStringEventId_p_void_p_sml__Kernel_p_q_const__char__std__string.getCPtr(value));
  }

  public SWIGTYPE_p_f_sml__smlStringEventId_p_void_p_sml__Kernel_p_q_const__char__std__string getM_Handler() {
    long cPtr = smlJNI.StringEventHandlerPlusData_m_Handler_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SWIGTYPE_p_f_sml__smlStringEventId_p_void_p_sml__Kernel_p_q_const__char__std__string(cPtr, false);
  }

  public StringEventHandlerPlusData() {
    this(smlJNI.new_StringEventHandlerPlusData__SWIG_0(), true);
  }

  public StringEventHandlerPlusData(int eventID, SWIGTYPE_p_f_sml__smlStringEventId_p_void_p_sml__Kernel_p_q_const__char__std__string handler, SWIGTYPE_p_void userData, int callbackID) {
    this(smlJNI.new_StringEventHandlerPlusData__SWIG_1(eventID, SWIGTYPE_p_f_sml__smlStringEventId_p_void_p_sml__Kernel_p_q_const__char__std__string.getCPtr(handler), SWIGTYPE_p_void.getCPtr(userData), callbackID), true);
  }

}