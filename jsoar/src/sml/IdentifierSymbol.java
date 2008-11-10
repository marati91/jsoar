/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 1.3.35
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package sml;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class IdentifierSymbol {
    // The value for this id, which is a string identifier (e.g. I3)
    // We'll use upper case for Soar IDs and lower case for client IDs
    // (sometimes the client has to generate these before they are assigned by the kernel)
    String m_Symbol ;

    // The list of WMEs owned by this identifier.
    // (When we delete this identifier we'll delete all these automatically)
    final List<WMElement>       m_Children = new LinkedList<WMElement>();

    // The list of WMEs that are using this symbol as their identifier
    // (Usually just one value in this list)
    final LinkedList<Identifier>      m_UsedBy = new LinkedList<Identifier>();

    // This is true if the list of children of this identifier was changed.  The client chooses when to clear these flags.
    boolean m_AreChildrenModified ;

  public synchronized void delete() {
      assert GetNumberUsing() == 0;
      
      DeleteAllChildren();
  }

  public IdentifierSymbol(Identifier pIdentifier) {
      m_UsedBy.add(pIdentifier);
  }

  public String GetIdentifierSymbol() {
      return m_Symbol;
  }

  public void SetIdentifierSymbol(String pID) {
      m_Symbol = pID;
  }

  public boolean AreChildrenModified() {
      return m_AreChildrenModified;
  }

  public void SetAreChildrenModified(boolean state) {
      m_AreChildrenModified = state;
  }

  public void NoLongerUsedBy(Identifier pIdentifier) {
      m_UsedBy.remove(pIdentifier);
  }

  public void UsedBy(Identifier pIdentifier) {
      m_UsedBy.add(pIdentifier);
  }

  public boolean IsFirstUser(Identifier pIdentifier) {
      if(m_UsedBy.isEmpty())
      {
          return false;
      }
      return pIdentifier == m_UsedBy.getFirst();
  }

  public Identifier GetFirstUser() {
      return m_UsedBy.getFirst();
  }

  public int GetNumberUsing() {
      return m_UsedBy.size();
  }

  public void AddChild(WMElement pWME) {
      SetAreChildrenModified(true);
      
      WMElement it = WMElement.findByTimeTag(m_Children, pWME.GetTimeTag());
      if(it == null)
      {
          m_Children.add(pWME);
      }
  }

  public void DeleteAllChildren() {
      for(WMElement pWME : m_Children)
      {
          pWME.delete();
      }
      m_Children.clear();
  }

  public void RemoveChild(WMElement pWME) {
      Iterator<WMElement> it = m_Children.iterator();
      while(it.hasNext())
      {
          if(pWME.GetTimeTag() == it.next().GetTimeTag())
          {
              it.remove();
              return;
          }
      }
  }

}
