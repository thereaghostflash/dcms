package CenterServerOrb;


/**
* CenterServerOrb/CenterServerPOA.java .
* 由IDL-to-Java 编译器 (可移植), 版本 "3.2"生成
* 从CenterServerOrb.idl
* 2018年6月12日 星期二 下午07时46分10秒 EDT
*/

public abstract class CenterServerPOA extends org.omg.PortableServer.Servant
 implements CenterServerOrb.CenterServerOperations, org.omg.CORBA.portable.InvokeHandler
{

  // Constructors

  private static java.util.Hashtable _methods = new java.util.Hashtable ();
  static
  {
    _methods.put ("createTRecord", new java.lang.Integer (0));
    _methods.put ("createSRecord", new java.lang.Integer (1));
    _methods.put ("getRecordCounts", new java.lang.Integer (2));
    _methods.put ("editRecord", new java.lang.Integer (3));
    _methods.put ("transferRecord", new java.lang.Integer (4));
    _methods.put ("shutdown", new java.lang.Integer (5));
  }

  public org.omg.CORBA.portable.OutputStream _invoke (String $method,
                                org.omg.CORBA.portable.InputStream in,
                                org.omg.CORBA.portable.ResponseHandler $rh)
  {
    org.omg.CORBA.portable.OutputStream out = null;
    java.lang.Integer __method = (java.lang.Integer)_methods.get ($method);
    if (__method == null)
      throw new org.omg.CORBA.BAD_OPERATION (0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);

    switch (__method.intValue ())
    {
       case 0:  // CenterServerOrb/CenterServer/createTRecord
       {
         String managerId = in.read_string ();
         String firstName = in.read_string ();
         String lastName = in.read_string ();
         String address = in.read_string ();
         String phone = in.read_string ();
         String specialization = in.read_string ();
         String location = in.read_string ();
         String $result = null;
         $result = this.createTRecord (managerId, firstName, lastName, address, phone, specialization, location);
         out = $rh.createReply();
         out.write_string ($result);
         break;
       }

       case 1:  // CenterServerOrb/CenterServer/createSRecord
       {
         String managerId = in.read_string ();
         String firstName = in.read_string ();
         String lastName = in.read_string ();
         String courseRegistered[] = CenterServerOrb.listHelper.read (in);
         String status = in.read_string ();
         String statusDate = in.read_string ();
         String $result = null;
         $result = this.createSRecord (managerId, firstName, lastName, courseRegistered, status, statusDate);
         out = $rh.createReply();
         out.write_string ($result);
         break;
       }

       case 2:  // CenterServerOrb/CenterServer/getRecordCounts
       {
         String managerId = in.read_string ();
         String $result = null;
         $result = this.getRecordCounts (managerId);
         out = $rh.createReply();
         out.write_string ($result);
         break;
       }

       case 3:  // CenterServerOrb/CenterServer/editRecord
       {
         try {
           String managerId = in.read_string ();
           String recordID = in.read_string ();
           String fieldName = in.read_string ();
           String newValue = in.read_string ();
           String $result = null;
           $result = this.editRecord (managerId, recordID, fieldName, newValue);
           out = $rh.createReply();
           out.write_string ($result);
         } catch (CenterServerOrb.CenterServerPackage.except $ex) {
           out = $rh.createExceptionReply ();
           CenterServerOrb.CenterServerPackage.exceptHelper.write (out, $ex);
         }
         break;
       }

       case 4:  // CenterServerOrb/CenterServer/transferRecord
       {
         try {
           String managerId = in.read_string ();
           String recordID = in.read_string ();
           String remoteCenterServerName = in.read_string ();
           String $result = null;
           $result = this.transferRecord (managerId, recordID, remoteCenterServerName);
           out = $rh.createReply();
           out.write_string ($result);
         } catch (CenterServerOrb.CenterServerPackage.except $ex) {
           out = $rh.createExceptionReply ();
           CenterServerOrb.CenterServerPackage.exceptHelper.write (out, $ex);
         }
         break;
       }

       case 5:  // CenterServerOrb/CenterServer/shutdown
       {
         this.shutdown ();
         out = $rh.createReply();
         break;
       }

       default:
         throw new org.omg.CORBA.BAD_OPERATION (0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);
    }

    return out;
  } // _invoke

  // Type-specific CORBA::Object operations
  private static String[] __ids = {
    "IDL:CenterServerOrb/CenterServer:1.0"};

  public String[] _all_interfaces (org.omg.PortableServer.POA poa, byte[] objectId)
  {
    return (String[])__ids.clone ();
  }

  public CenterServer _this() 
  {
    return CenterServerHelper.narrow(
    super._this_object());
  }

  public CenterServer _this(org.omg.CORBA.ORB orb) 
  {
    return CenterServerHelper.narrow(
    super._this_object(orb));
  }


} // class CenterServerPOA
