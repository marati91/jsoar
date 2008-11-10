/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 1.3.35
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package sml;

public class ConnectionInfo {
    private final String m_ID ;
    private final String m_Name ;
    private final String m_Status ;
    private final String m_AgentStatus ;
    
    public ConnectionInfo(String pID, String pName, String pStatus, String pAgentStatus)
    {
        m_ID     = pID ;
        m_Name   = (pName != null ? pName : "unknown-name") ;
        m_Status = (pStatus != null ? pStatus : "unknown-status") ;
        m_AgentStatus = (pAgentStatus != null ? pAgentStatus : "unknown-status") ;
    }

  public synchronized void delete() {
  }


  public String GetID() {
      return m_ID;
  }

  public String GetName() {
      return m_Name;
  }

  public String GetConnectionStatus() {
      return m_Status;
  }

  public String GetAgentStatus() {
      return m_AgentStatus;
  }

}
