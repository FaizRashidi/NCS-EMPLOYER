/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tms.hrdc.defaultPluginTool;

import com.tms.hrdc.dao.EmpmObj;
import com.tms.hrdc.dao.OtherContactsUser;
import com.tms.hrdc.datalistAction.DeleteUserAndRecord;
import com.tms.hrdc.util.CommonUtils;
import com.tms.hrdc.util.Constants;
import com.tms.hrdc.util.DBHandler;
import com.tms.hrdc.util.MasterSetupData;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.directory.dao.UserDao;
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryManager;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.json.JSONException;

/**
 *
 * @author faizr
 */
public class SchedulerArchiveDataImport_Dereg extends DefaultApplicationPlugin {
    
    @Override
    public String getName() {
        return this.getClassName();
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getDescription() {
        return "Scheduler for Legacy Deregistration Data Imports";
    }
    
    @Override
    public String getLabel() {
        return "HRDC - EMPM - Scheduler Archive Dereg Data Imports";
    }
    
    @Override
    public String getClassName() {
        return this.getClass().toString();
    }
    
    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/email_template_toolx.json", null, true, null);
    }
    
    private void msg(String msg){
        LogUtil.info(this.getClassName(), msg);
    }
    
    
    private int LIMIT_QUERY = 0;
    int MIGRATED_EMP_COUNT = 0;
    private String EMAIL = "faiz.rashidi@tmsasia.com;munfatt.lai@tmsasia.com";
    
    ArrayList ACTIVE_ACCOUNT = new ArrayList();
    
    private int LIMIT_ACCOUNT = 0;
    
    private void setAccounts(){
        ACTIVE_ACCOUNT.add("148712D");
        ACTIVE_ACCOUNT.add("130212D");
        ACTIVE_ACCOUNT.add("102650K");
        ACTIVE_ACCOUNT.add("00000076659T");
        ACTIVE_ACCOUNT.add("DBKK490504");        
        ACTIVE_ACCOUNT.add("13857V");
        ACTIVE_ACCOUNT.add("13857V_2");
        LIMIT_ACCOUNT = ACTIVE_ACCOUNT.size();
    }
    
    private String START_TIME = "";
    private String END_TIME = "";
    private String ERROR = "";
    private String TOTAL_SIZE = "0";
    private String TITLE = "EMP. DEREGISTRATION DATA MIGRATION";
    
    @Override
    public Object execute(Map props) {     
                
        setAccounts();
        Thread checkingThread = new PluginThread(new Runnable() {
            @Override
            public void run() {
                DBHandler db = new DBHandler();   
                
                try{
                    db.openConnection();
                    
                    START_TIME = CommonUtils.get_DT_CurrentDateTime("dd-MM-YYYY HH:mm:ss");
                    
                    msg("QUERYING ARCHIVE EMPLOYER_DEREGISTRATION... ");
                    ArrayList<HashMap<String, String>> arcEmpRegList = getArchiveEmployerDeregData(db);
                    msg("QUERYING ARCHIVE EMPLOYER_DEREGISTRATION COMPLETE ... TOTAL SIZE "+Integer.toString(arcEmpRegList.size()));
                    TOTAL_SIZE = Integer.toString(arcEmpRegList.size());
                    migrateIntoNCS(db, arcEmpRegList);
                    
                    String content = "Archive Data Size "+Integer.toString(arcEmpRegList.size())+". Inserted: "+Integer.toString(INSERT_COUNT);
                    
                    sendEmail(content);
                    
                }catch(Exception e){
                    e.printStackTrace();
                }finally{
                    db.closeConnection();
                }
            }            
        });
        
        checkingThread.setDaemon(true);
        checkingThread.start();
        
        return null;
    }
    
    int INSERT_COUNT = 0;
    
    private ArrayList<HashMap<String, String>> getArchiveEmployerDeregData(DBHandler db) {
        
        ArrayList<HashMap<String, String>> list = new ArrayList();
        
        list = db.select(
               "SELECT \n" +
                " EMPR_DEREG_TXN_ID, \n" +
                "-- e.EMPLOYER_ID, r.c_mycoid, r.c_comp_name, r.c_empl_email_pri,r.c_empl_tel_no_pri, \n" +
                "DEREG_APPLICANT_NAME, DEREG_APPLICANT_DESIGNATION, DEREG_REMARKS,\n" +
                "DEREG_DECLARATION_DT, \n" +
                "d.CREATED_DATE, d.CREATED_BY, d.CREATOR_NAME,\n" +
                "d.UPDATED_DATE, d.UPDATED_BY, d.UPDATER_NAME,\n" +
                "DEREG_EFF_DATE, DEREG_PRORATED_AMT, DEREG_INTRST_REFUND_AMT,\n" +
                "DEREG_RESRV_ONE_RESRV_TWO_AMT, DEREG_INCENTIVE_AMT, \n" +
                "CONCAT(DEREG_ADDTNL_REMARKS, ' ', DEREG_SUBREASON) AS SUB_REASON,\n" +
                "APPLICATION_STATUS, d.END_DATE, DEREG_REASON, DEREG_REASON_ID  \n" +
                "FROM archive.employer_deregistration d\n" +
                "where \n" +
                "APPLICATION_STATUS IS NOT NULL \n" +
                "and APPLICATION_STATUS != ''  \n" +
                "AND NOT EXISTS( select id FROM app_fd_empm_dereg a WHERE a.id = d.EMPR_DEREG_TXN_ID )\n" + 
                "order by EMPR_DEREG_TXN_ID asc " +
                (LIMIT_QUERY==0?"":" limit "+Integer.toString(LIMIT_QUERY))
           );
                   
        return list;
    }
    
    private void migrateIntoNCS(DBHandler db, ArrayList<HashMap<String, String>> list) {
        
        msg("MIGRATING DATA.. "+list.get(0).toString());
     
        String id = "";
        String empId = "";
        String mycoid = "";
        String comp_name = "";
        String email_pri = "";
        String tel_no_pri = "";
        String appl_name = "";
        String appl_desg = "";
        String dereg_remarks = "";
        String declaration_dt = "";
        
        String created_dt = "";
        String created_by = "";
        String creator_name = "";
        
        String updated_dt = "";
        String updated_by = "";
        String updator_name = "";
        
        String dereg_eff_dt = "";
        String dereg_prorated_amt = "";
        String dereg_intrst_refund_amt = "";
        String dereg_resrve_one_two = "";
        String incentive_amt = "";
        String sub_reason = "";
        String appl_status = "";
        String end_dt = "";
        String status = "";
        
        String reason = "";
        String reason_id = "";
        
        for(HashMap<String, String> dHm:list){
            
            id = dHm.getOrDefault("EMPR_DEREG_MST_ID","UUID()");
            empId = dHm.getOrDefault("EMPLOYER_ID","");            
            appl_name = dHm.getOrDefault("DEREG_APPLICANT_NAME","");
            appl_desg = dHm.getOrDefault("DEREG_APPLICANT_DESIGNATION","");
            dereg_remarks = dHm.getOrDefault("DEREG_REMARKS","");
            declaration_dt = dHm.getOrDefault("DEREG_DECLARATION_DT","");

            created_dt = dHm.getOrDefault("CREATED_DATE","");
            created_by = dHm.getOrDefault("CREATED_BY","");
            creator_name = dHm.getOrDefault("CREATOR_NAME","");

            updated_dt = dHm.getOrDefault("UPDATED_DATE","");
            updated_by = dHm.getOrDefault("UPDATED_BY","");
            updator_name = dHm.getOrDefault("UPDATER_NAME","");

            dereg_eff_dt = dHm.getOrDefault("DEREG_EFF_DATE","");
            dereg_prorated_amt = dHm.getOrDefault("DEREG_PRORATED_AMT","");
            dereg_intrst_refund_amt = dHm.getOrDefault("DEREG_INTRST_REFUND_AMT","");
            dereg_resrve_one_two = dHm.getOrDefault("DEREG_RESRV_ONE_RESRV_TWO_AMT","");
            incentive_amt = dHm.getOrDefault("DEREG_INCENTIVE_AMT","");
            sub_reason = dHm.getOrDefault("SUB_REASON","");
            appl_status = dHm.getOrDefault("APPLICATION_STATUS","");
            end_dt = dHm.getOrDefault("END_DATE","");
            
            
            updated_dt = updated_dt.isEmpty()?end_dt:updated_dt;
            
            reason = dHm.getOrDefault("DEREG_REASON","");
            reason_id = dHm.getOrDefault("DEREG_REASON_ID","");
            
            msg("DATA "+empId+" id "+id);
            
            switch(reason_id){
                case "1000111":
                    reason = "Cease Operation";
                break;
                case "1000112":
                    reason = "Less Than 10 Employees";
                break;
                case "1000113":
                    reason = "Business activities not covered under Act";
                break;
                case "1000114":
                    reason = "Merger/Restructuring";
                break;
            }
            
            EmpmObj emp = new EmpmObj(db, EmpmObj.BY_ID, empId);
            
//            HashMap<String, String> hm = db.selectOneRecord(
//                    "SELECT c_mycoid, c_comp_name, c_empl_email_pri, c_empl_tel_no_pri "
//                    + "FROM app_fd_empm_reg WHERE id = ?", 
//                    new String[]{empId}
//            );
            
//            if(emp!=null){
            if(emp.getEmpData()!=null){
                HashMap<String, String> hm = emp.getEmpData();
                mycoid = hm.getOrDefault("c_mycoid","");
                comp_name = hm.getOrDefault("c_comp_name","");
                email_pri = hm.getOrDefault("c_empl_email_pri","");
                tel_no_pri = hm.getOrDefault("c_empl_tel_no_pri","");
            }else{
                continue;
            }
            
            status = "APPROVED";
            
            //if status approved
            if(appl_status.equals("Approve")){          
                
                
                emp.updateField("c_data_status", "DEREGISTER_APPROVED");
                emp.updateField("c_emp_status", "INACTIVE");
                try {
                    DeregisterTool.disableUser(db, emp);
                } catch (Exception ex) {
                    msg("Disabling user error "+ex.getMessage());
                }
            }else if(appl_status.equals("Rejected")){
                status = "REJECTED";
            }else{
                
            }
            
            String query = 
                    "INSERT INTO app_fd_empm_dereg (id, "
                    + "dateCreated, createdBy, createdByName,"
                    + "dateModified, modifiedBy, modifiedByName, "
                    + "c_dreg_emp_id, c_submit_mode, "
                    + "c_dreg_mycoid, c_comp_name, c_submitting_name, c_submitting_desg,"
                    + "c_submitting_tel_no_pri, c_submitting_email_pri, "
                    + "c_dreg_remarks, c_cease_ops_dt, c_form_type, c_effective_dt,"
                    + "c_levy_refund, c_prorated_amt,c_amt_reserve_1_2, c_incentive_amt,"
                    + "c_approve_dt, c_flow_status, c_dereg_refno, c_approve_remark, c_dreg_reason "
                    + ") VALUES ("
                    + (id.contains("UUID")?"UUID()":"'"+id+"'") + ", '"+created_dt+"','"+creator_name+"','"+creator_name+"',"
                    + "'"+updated_dt+"','"+updator_name+"','"+updator_name+"',"
                    + "'"+empId+"','ONLINE',"
                    + "'"+mycoid+"','"+comp_name+"','"+appl_name+"','"+appl_desg+"',"
                    + "'"+tel_no_pri+"','"+email_pri+"',"
                    + "'"+dereg_remarks+"','"+declaration_dt+"','4','"+dereg_eff_dt+"',"
                    + "'"+dereg_intrst_refund_amt+"','"+dereg_prorated_amt+"','"+dereg_resrve_one_two+"','"+incentive_amt+"',"
                    + "'"+end_dt+"', '"+status+"', '"+id+"', '"+sub_reason+"', '"+reason+"')"
                    + "";
            
            msg("QUery = "+query);
           
            int i = db.update(query);
            INSERT_COUNT+=i;
//             msg("Insert query "+query+", RESULT "+Integer.toString(i));
            
        }
    }
    
    private void sendEmail(String customContent){
        END_TIME = CommonUtils.get_DT_CurrentDateTime("dd-MM-YYYY HH:mm:ss");
        
        String content = 
                TITLE+"<br /><br /> Start Time: "+START_TIME+" <br /> "
                + "Content : "+customContent+" <br />"
                +"End Time: "+END_TIME+" <br /> ";
        
        CommonUtils.sendEmail(EMAIL, "", "MIGRATION RESULT", content, null, null);
    }
    
}