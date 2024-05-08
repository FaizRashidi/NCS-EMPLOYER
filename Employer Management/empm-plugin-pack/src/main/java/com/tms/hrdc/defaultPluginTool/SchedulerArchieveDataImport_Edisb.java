/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tms.hrdc.defaultPluginTool;

import com.tms.hrdc.dao.EmpmObj;
import com.tms.hrdc.util.CommonUtils;
import com.tms.hrdc.util.Constants;
import com.tms.hrdc.util.DBHandler;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.plugin.base.DefaultApplicationPlugin;

/**
 *
 * @author faizr
 */
public class SchedulerArchieveDataImport_Edisb  extends DefaultApplicationPlugin {
    
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
        return "Scheduler for Legacy Data Imports";
    }
    
    @Override
    public String getLabel() {
        return "HRDC - EMPM - Scheduler Archive Data  eDisb Imports";
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
    int INSERT_COUNT = 0;
    
    private String START_TIME = "";
    private String END_TIME = "";
    private String ERROR = "";
    private String TITLE = "EMPLOYER EDISBURSEMENT DATA MIGRATION";
    
    @Override
    public Object execute(Map props) {     
                
        Thread checkingThread = new PluginThread(new Runnable() {
            @Override
            public void run() {
                DBHandler db = new DBHandler();   
                
                try{
                    db.openConnection();
                    
                    START_TIME = CommonUtils.get_DT_CurrentDateTime("dd-MM-YYYY HH:mm:ss");
                    
                    msg("QUERYING ARCHIVE EMP EDISB... ");
                    ArrayList<HashMap<String, String>> arcEmpRegList = getArchiveEmployerData(db);
                    msg("QUERYING ARCHIVE EMP EDISB COMPLETE ... TOTAL SIZE "+Integer.toString(arcEmpRegList.size()));
                    
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
    
    private ArrayList<HashMap<String, String>> getArchiveEmployerData(DBHandler db) {
        
        ArrayList<HashMap<String, String>> list = new ArrayList();
        
        list = db.select(
                "select \n" +
                "d.EDISBURSEMENT_TXN_ID,\n" +
                "d.CREATED_DATE, d.UPDATED_DATE,\n" +
                "d.CREATOR_NAME, d.UPDATER_NAME,\n" +
                "r.MYCO_ID, r.COMPANY_NAME, r.EMPLOYER_ID,\n" +
                "d.COMP_NAME_AS_PER_BANK, d.EDISBURSE_ACC_NO, \n" +
                "d.BANK_NAME, d.BANK_CODE, d.BRANCH_NAME,\n" +
                "d.STATUS\n" +
                "from archive.employer_edisbursement d\n" +
                "INNER JOIN archive.employer_registration r ON r.EMPLOYER_ID = d.EMPLOYER_ID\n" +
                "where NOT EXISTS\n" +
                "(\n" +
                "	SELECT 1 FROM app_fd_empm_disburse a where a.id = d.EDISBURSEMENT_TXN_ID\n" +
                ") " +
                 (LIMIT_QUERY==0?"":" limit "+Integer.toString(LIMIT_QUERY))
        );
        return list;
    }
    
    ArrayList savedDisb = new ArrayList();
    ArrayList<HashMap<String,String>> bankDataList =  new ArrayList();
    ArrayList<HashMap<String,String>> bankBranchDataList =  new ArrayList();
    
    private void migrateIntoNCS(DBHandler db, ArrayList<HashMap<String, String>> list) {
        
//        msg("MIGRATING DATA.. "+list.get(0).toString());
        setBankData(db);
        
        msg("Banks Data = "+bankDataList.toString());
     
        String id = "";
        String empId = "";
        String mycoid = "";
        String comp_name = "";
        
        String compNameAsPerBank = "";
        String eDisbAccNo = "";
        String bankName = "";
        String bankCode = "";
        String branchName = "";
        String status = "";
        
        String created_dt = "";
        String created_by = "";
        String creator_name = "";
        
        String updated_dt = "";
        String updated_by = "";
        String updator_name = "";
        
        int count = 0;
                
        for(HashMap<String, String> dHm:list){
            
            id = dHm.getOrDefault("EDISBURSEMENT_TXN_ID","UUID()");
            empId = dHm.getOrDefault("EMPLOYER_ID","");            
            mycoid = dHm.getOrDefault("MYCO_ID","");            
            comp_name = dHm.getOrDefault("COMPANY_NAME","");            

            created_dt = dHm.getOrDefault("CREATED_DATE","");
            creator_name = dHm.getOrDefault("CREATOR_NAME","");

            updated_dt = dHm.getOrDefault("UPDATED_DATE","");
            updator_name = dHm.getOrDefault("UPDATER_NAME","");
            
            compNameAsPerBank = dHm.getOrDefault("COMP_NAME_AS_PER_BANK","");
            eDisbAccNo = dHm.getOrDefault("EDISBURSE_ACC_NO","");
            bankName = dHm.getOrDefault("BANK_NAME","");
            bankCode = dHm.getOrDefault("BANK_CODE","");
            branchName = dHm.getOrDefault("BRANCH_NAME","");
            status = dHm.getOrDefault("STATUS","");
            
            updated_dt = updated_dt.isEmpty()?created_dt:updated_dt;
            
//            msg("DATA "+empId+" id "+id);            
            
            if(status.equals("DeActive")){
                status = "Inactive";
            }
            
            String bankId = getBankId(db, bankName, bankCode);
            String branchId = getBranchId(db, bankId, branchName);
            
            String query = 
                    "INSERT INTO app_fd_empm_disburse (id, "
                    + "dateCreated, createdBy, createdByName,"
                    + "dateModified, modifiedBy, modifiedByName, "
                    + "c_disb_ref_no, "
                    + "c_mycoid, c_comp_name, "
                    + "c_emp_fk, c_bankId, c_bank_acc_no, c_comp_name_bank_stmt,"
                    + "c_bank_branch, c_status "
                    + ") VALUES ("
                    + (id.contains("UUID")?"UUID()":"'"+id+"'") + ", '"
                    +created_dt+"','"+creator_name+"','"+creator_name+"',"
                    + "'"+updated_dt+"','"+updator_name+"','"+updator_name+"',"
                    + "'EDISB/ARCHIVE/"+id+"', "
                    + "'"+mycoid+"','"+comp_name+"','"+empId+"',"
                    + "'"+bankId+"','"+eDisbAccNo+"',"
                    + "'"+compNameAsPerBank+"','"+branchId+"','"+status+"' )";
            
//            );
if(count%1500 == 0){
    msg("Insert count = "+Integer.toString(count)+", query "+query);
}
count++;
            if(!savedDisb.contains(id)){
                
                try{
                    int i = db.update(query);
                    INSERT_COUNT+=i;
                    savedDisb.add(id);
                }catch(Exception e){
                    msg("Duplicate id = "+e.toString());
                    e.printStackTrace();
                }                
            }
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

    private String getBankId(DBHandler db, String bankName, String bankCode) {
        String id = bankDataList.stream()
                .filter(
                        map -> bankName.equals(map.get("c_bank_name")) && 
                                bankCode.equals(map.get("c_swift_code"))
                )
                .map(map -> map.get("id"))
                .findFirst()
                .orElse("");
        
        if(id.isEmpty()){
            HashMap bHm = new HashMap();
            bHm.put("bank_name", bankName);
            bHm.put("swift_code", bankCode);

            id = CommonUtils.saveUpdateForm2("master_setup","bank_stp", "", bHm);  
            
            bHm.put("id", id);
            bankDataList.add(bHm);
        }
        
        return id;
    }

    private String getBranchId(DBHandler db, String bankId, String branchName) {
//        Optional<String> idOptional = bankDataList.stream()
//                .filter(
//                        map -> bankId.equals(map.get("c_parent_bank")) && 
//                        branchName.equals(map.get("c_branch_name"))
//                )
//                .map(map -> map.get("id"))
//                .findFirst();

        String id = bankBranchDataList.stream()
                .filter(
                        map -> bankId.equals(map.get("c_parent_bank")) && 
                                branchName.equals(map.get("c_branch_name"))
                )
                .map(map -> map.get("id"))
                .findFirst()
                .orElse("");
        
        if(id.isEmpty()){
            HashMap bHm = new HashMap();
            bHm.put("parent_bank", bankId);
            bHm.put("branch_name", branchName);

            id = CommonUtils.saveUpdateForm2("master_setup","bank_stp", "", bHm);  
            
            bHm.put("id", id);
            bankBranchDataList.add(bHm);
        }
        
        return id;
    }

    private void setBankData(DBHandler db) {
        bankDataList = db.select(
        "SELECT * FROM app_fd_stp_bank"
        );
        
        bankBranchDataList = db.select(
        "SELECT * FROM app_fd_stp_bank_branch"
        );
    }
    
}
