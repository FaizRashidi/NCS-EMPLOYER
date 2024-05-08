package com.tms.hrdc.datalistAction;

import com.tms.hrdc.binder.PotEmpExcelBinder;
import com.tms.hrdc.dao.CurrentUser;
import com.tms.hrdc.dao.EmpmObj;
import com.tms.hrdc.datalistAction.peDataListImpl.SetNewUplToPE;
import com.tms.hrdc.util.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;

/**
 *
 * @author faizr
 */
public class PotEmpStateChangerButton extends DataListActionDefault {

    public String getName() {
        return this.getClass().toString(); 
    }

    public String getVersion() {
        return "1.0";
    }

    public String getDescription() {
        return "Potential Employer Action Button"; 
    }

    public String getLinkLabel() {
        String label = getPropertyString("label");
        if (label == null || label.isEmpty()) {
            label = "Delete User & Record";
        }
        return label;
    }
    
    @Override
    public String getHref() {
        return getPropertyString("href"); 
    }

    @Override
    public String getTarget() {
        return "post";
    }

    @Override
    public String getHrefParam() {
        return getPropertyString("hrefParam"); 
    }

    @Override
    public String getHrefColumn() {
        return getPropertyString("hrefColumn");
    }
    
    public String getConfirmation() {
        String confirm = getPropertyString("confirmation");
        if (confirm == null || confirm.isEmpty()) {
            confirm = "Confirm?";
        }
        return confirm;
    }

    public String getLabel() {
        return "HRDC - EMPM - PE Multi-Function Button";
    }

    public String getClassName() {
        return this.getClass().getName();
    }

    public String getPropertyOptions() {
        String json = "[{\n"
                + "    title : 'Application & User Delete Button',\n"
                + "    properties : [{\n"
                + "        label : 'Label',\n"
                + "        name : 'label',\n"
                + "        type : 'textfield',\n"
                + "        description : 'Potential Employer Button',\n"
                + "        value : 'Potential Employer Button'\n"
                + "    },{\n" 
                + "            name: 'type', " 
                + "            label: 'PE Button Type', " 
                + "            type: 'radio', " 
                + "            options : [\n" 
                + "                {value: 'add_to_PE', label : 'Add to Potential Employer'}," 
                + "                {value: 'add_to_truePE', label : 'Add to True Potential Employer'}," 
                + "                {value: 'add_to_dirtyList', label : 'Add to Dirty List'}," 
                + "                {value: 'select_curr_potential', label : 'Select current PE from duplicate'}," 
                + "                {value: 'flush_out', label : 'Flush Out'}," 
                + "                {value: 'flush_out_uploaded_peBatch', label : 'Flush Out NewlyUploaded PE'}," 
                + "                {value: 'write_off', label : 'Write-Off'}," 
                + "                {value: 'sub_batch', label : 'Sub Batch'}," 
                + "                {value: 'complaint', label : 'Complaint'}," 
                + "                {value: 'cksp', label : 'CKSP'}" 
                + "            ]\n" 
                + "        }]\n"
                + "}]";
        return json;
    }
    
    int count = 0,updateCount = 0, failedCount = 0, existCount = 0;;
    String additional_msg = "";
    String CURRENTUSERNAME = "";

    // new to pe counts
    int pe_inserted = 0, pe_need_action = 0, pe_failed = 0;
    String overwritten = "", failed = "";
    
    int itemCount = 0;
    int batchCount = 0;
    
    private void msg(String msg){
        LogUtil.info(this.getClass().toString(), msg);
    }
    
    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        
        DBHandler db = new DBHandler();
        try {
            db.openConnection((DataSource) AppUtil.getApplicationContext().getBean("setupDataSource"));
        } catch (SQLException ex) {
            ex.printStackTrace();
            db.closeConnection();
        } 
          
        String type = (String) getProperty("type");

        String writeOffId = "";
        String engId = "";
        
        msg("Type "+type);
        
        CURRENTUSERNAME = new CurrentUser().getFullName();
        
        String newBatchId = ""; //combining batches
        String newBatchLabel = ""; //combining batches
        String batch = "";
        int peCount = 0;
        
        ArrayList mergedBatchId = new ArrayList();
        ArrayList mergedBatchNames = new ArrayList();
        
        String message = "";
        String redirect_url = "REFERER";
        
        switch(type){
            case "add_to_PE":
                SetNewUplToPE snp = new SetNewUplToPE(db, rowKeys);
                snp.handleNewToPE();
                message = snp.getReturnMessage();
                
            break;
        }
        
        for(String id:rowKeys){
//            LogUtil.info("rowkye",id);
            ArrayList<HashMap<String, String>> pList = isIDBatch(db, id);
            String peId = "";
            String empId = "";
//            LogUtil.info("rowkye 1",pList.toString());            // for adding to PE
            
//            LogUtil.info("rowkye 2",pList.toString());
            
            switch(type){
                case "add_to_PE":    ;
//                    
//                    String uplFk = "";
//                    String batchName = "";
//                    
//                    pList = getAllUploadedSuccessItem(db, id); //list of pe in upl_data (newly uploaded)       
//                    
//                    for(HashMap pe:pList){
//                        
//                        batch = pe.get("c_batch")==null?"":pe.get("c_batch").toString();
//                        empId = pe.get("c_emp_fk")==null?"":pe.get("c_emp_fk").toString();
//                        uplFk = pe.get("id")==null?"":pe.get("id").toString();
//                        batchName = pe.get("batch_name")==null?"":pe.get("batch_name").toString();
//                        String mycoid = pe.get("mycoid")==null?"":pe.get("mycoid").toString();
//                        
//                        msg("Batch - "+batch+" Name "+batchName+" peCount "+Integer.toString(peCount));
//                        
//                        if(!mergedBatchNames.contains(batchName)){
//                            mergedBatchNames.add(batchName);
//                        }
//                        
//                        if(peCount==0){
//                            newBatchId = batch;
//                            msg("Batch - "+batch+" Name "+batchName+" ==> SELECTED AS MAIN MERGED BATCH");
//                        }else {                            
//                            if(!batch.equals(newBatchId) && !mergedBatchId.contains(batch)){
//                                mergedBatchId.add(batch.trim());
//                            }                            
//                        }
//                        
//                        peCount++;                        
//                        handleImportToPE(db, uplFk, batch, empId, mycoid, newBatchId);
//                    }         
//                    
//                    String mergedBatchIdStr = 
//                            "'"+mergedBatchId.toString()
//                                    .replace("[", "")
//                                    .replace("]", "")
//                                    .replace(",", "','")+"'";
//                    
//                    String mergedBatchNamesStr = 
//                                    mergedBatchNames.toString()
//                                    .replace("[", "")
//                                    .replace("]", "");
//                    
//                    msg("mergedBatchId "+mergedBatchIdStr);
//                    msg("mergedBatchNamesStr "+mergedBatchNamesStr);
//                    
//                    //import if by pe
//                    db.update(
//                            "UPDATE app_fd_empm_pe_potEmp SET c_batch = ? WHERE c_batch = ?",
//                            new String[]{newBatchId},
//                            new String[]{batch}
//                    );
//                    
//                    //rename batch
//                    db.update("UPDATE app_fd_empm_pe_file_upl\n" +
//                        "SET c_isMerged = 'Y' " +
//                        "WHERE id IN ("+mergedBatchIdStr+")"
//                    );
//                                        
//                    //rename batch
//                    db.update("UPDATE app_fd_empm_pe_file_upl\n" +
//                        "SET c_batch_new_name = ? \n" +
//                        "WHERE id = ? ",
//                            new String[]{mergedBatchNamesStr},
//                            new String[]{newBatchId}
//                    );
//                    
//                    message = "Succesully submitted to PE: "+ Integer.toString(pe_inserted)+". "+
//                            "Existing PE Pending Action: "+Integer.toString(pe_need_action)+". "+
//                            "Failed: "+Integer.toString(pe_failed);
                break;
                case "add_to_truePE":
                    for(HashMap pe:pList){
                        peId = pe.get("id")==null?"":pe.get("id").toString();
                        empId = pe.get("c_emp_fk")==null?"":pe.get("c_emp_fk").toString();
                        handleImportToTrue(db, peId, empId);   
                    }
                    message = "Passed criteria: "+Integer.toString(updateCount)+", "+
                                "Not passed criteria "+Integer.toString(failedCount);
                break;
                case "add_to_dirtyList":
                    for(HashMap pe:pList){
                        peId = pe.get("id")==null?"":pe.get("id").toString();
                        empId = pe.get("c_emp_fk")==null?"":pe.get("c_emp_fk").toString();
                        handleImportToDirty(db, peId, empId);   
                    }
                    message = Integer.toString(updateCount)+" sent to dirty list ";                    
                break;                
                case "select_curr_potential":
                    message = handleSelectPE(db, id);
//                    message = Integer.toString(updateCount)+" updated ";                    
                break;                
                case "flush_out":
//                    for(HashMap pe:pList){
//                        peId = pe.get("id")==null?"":pe.get("id").toString();
//                        empId = pe.get("c_emp_fk")==null?"":pe.get("c_emp_fk").toString();
//                        handleFlushOut(db, peId, empId);   
//                    }
                    
                    message = handleFlushOutNEW(db, id);
                    
//                    message = Integer.toString(updateCount)+" records flushed out ";
                break;         
                case "flush_out_uploaded_peBatch":
                    
                    db.update(
                            "UPDATE "+Constants.TABLE.POT_EMP_UPLOAD+" SET c_upl_status = ? WHERE id = ?",
                            new String[]{PotEmpExcelBinder.MSG_DELETED},
                            new String[]{id}
                    );
                                        
                    pList = getAllUploadedItem(db, id);
                    
                    for(HashMap pe:pList){
                        String uplItemId = pe.get("id")==null?"":pe.get("id").toString();
                        empId = pe.get("c_emp_fk")==null?"":pe.get("c_emp_fk").toString();
                        handleFlushOutUploadedItem(db, uplItemId, empId);        
                        
                        db.update(
                                "UPDATE "+Constants.TABLE.POT_EMP_UPLOAD+" SET c_total_row = c_total_row-1 WHERE id = '"+id+"'"
                        );
                    }
                    
                    db.delete(
                            "DELETE FROM "+Constants.TABLE.POT_EMP_UPLOAD+" WHERE id = ?",
                            new String[]{id}
                    );
                    
                    batchCount++;
                    
                    message = Integer.toString(itemCount)+" records flushed out \"\n\" "
                            + " from "+ Integer.toString(batchCount)+
                            " batches ";
                break;      
                case "write_off":                    
                    for(HashMap pe:pList){
                        peId = pe.get("id")==null?"":pe.get("id").toString();
                        empId = pe.get("c_emp_fk")==null?"":pe.get("c_emp_fk").toString();                        
                        writeOffId = getWriteOffId(db);
                        handleWriteOff(db, peId, writeOffId);    
                        startWriteOffProcess(writeOffId);
                    }                    
                    message = Integer.toString(updateCount)+" records pending for write-off ";
                break;
                case "complaint":
                    for(HashMap pe:pList){
                        peId = pe.get("id")==null?"":pe.get("id").toString();
                        empId = pe.get("c_emp_fk")==null?"":pe.get("c_emp_fk").toString();
                        sendToEnforcement(db, peId, empId);
                    }
                    message = Integer.toString(updateCount)+" sent to Enforcement ";
                break;
            }            
        }
        
        // message building 
        if(type.equals("sub_batch")){
            message = handleSubBatch(db, rowKeys);
            
            message = Integer.toString(count)+" records grouped in sub-batch "+message;
        }
        
        db.closeConnection();
        
        DataListActionResult result = new DataListActionResult();
        result.setType(DataListActionResult.TYPE_REDIRECT);
        result.setUrl(redirect_url);
        
        if(!message.isEmpty()){
            result.setMessage(message);
        }
        
        return result;
    }

    /**
     * 
     * @param db
     * @param uplId - app_fd_empm_pe_upl_data - temp pe id
     * @param batch - id from parent table (app_fd_empm_pe_fileUpl)
     * @param tempEmpId - id from app_fd_empm_reg_temp (temp emp data)
     * @param newBatchId - new id from fileupl (merging batch)
     */
    private void handleImportToPE(DBHandler db, String tempPeId, String batch, String tempEmpId, String tempMycoid, String newBatchId) {
        
        int countIn = 0;
        String query = "";
        ArrayList list = new ArrayList();
        boolean isMycoidExistAsPE = false;
        boolean override = false;
        boolean insert = false;

        // check if mycoid exist as potential
        // exists -> getreg ID, override with NEW & use new batch. Status is Potential
        // !exists -> create New. Status Potential
        // delete data from pe_upl_data & update

//        HashMap tempHm = db.selectOneRecord("SELECT * FROM "+Constants.TABLE.EMPREG_TEMP+" WHERE id = ?",
//                new String[]{tempEmpId}
//        );
//
//        if(tempHm==null){
//            return;
//        }

//        String tempMycoid = tempHm.getOrDefault("c_mycoid","").toString();
//        String tempTotalEmplCount = tempHm.getOrDefault("c_total_empl","").toString();        
        
//        ArrayList<HashMap<String, String>> existingTPEMycoidList = db.select(
//                "SELECT '' as c_batch, r.id, '' as existingPotEmpId, r.c_total_empl, r.c_comp_name, c_last_move, c_data_status " +
//                "FROM app_fd_empm_reg r " +
//                " WHERE c_mycoid = ? " ,
//                new String[]{tempMycoid}
//        );

        msg("This pe mycoid ==> "+tempMycoid);
        
        boolean isTPEExist = false;
        boolean isPEExist = false;
        String existMycoidStatus = "";
        
//        if(existingTPEMycoidList!=null && existingTPEMycoidList.size()>0){
//            isTPEExist = true;
//            
//            existMycoidStatus = existingTPEMycoidList.get(0).getOrDefault("c_last_move", "").toString();
//        }
                
        ArrayList<HashMap<String, String>> existingPEMycoidList = db.select(
                "select \n" +
                "p.c_batch, r.id as existingEmpId,  p.id as existingPotEmpId, r.c_total_empl, r.c_comp_name, c_status \n" +
                "FROM app_fd_empm_pe_potEmp p " +
//                "INNER JOIN app_fd_empm_pe_file_upl u ON u.id = p.c_batch " +
                "INNER JOIN app_fd_empm_reg r ON r.id = p.c_emp_fk\n" +
                "WHERE p.c_status = 'POTENTIAL'\n" +
                "and r.c_mycoid = ?  ",
                new String[]{tempMycoid}
        );
        
//        msg("EXISTING POTENTIALS ==> "+existingPEMycoidList.toString());
        
        String existingEmpId = "";
        String existingPotEmpId = "";
        String existingPotTotalEmplCount = "";

//        int existingPotTotalEmplCount_int = CommonUtils.strToInt(existingPotTotalEmplCount);
//        int tempTotalEmplCount_int = CommonUtils.strToInt(tempTotalEmplCount);

//        if(isMycoidExistAsPE && existingPotTotalEmplCount_int < tempTotalEmplCount_int){
//             override = true;
//        }
        if(existingPEMycoidList!=null && existingPEMycoidList.size()>0){
            isPEExist = true;
        }

        String empId = EmpUtil.createPEEmployer(db, tempEmpId, existingEmpId);
//        LogUtil.info(this.getClassName(),"UPDATE OTHER CONTACT DETAILS "+Integer.toString(i));

        // create potential employer data
        HashMap newPe = new HashMap();
        newPe.put("emp_fk", empId);
        newPe.put("batch", newBatchId);
        newPe.put("status", Constants.STATUS.POT_EMP.POTENTIAL);
        newPe.put("is_registered", "No");
        newPe.put("isPotEmp", "Y");        
        newPe.put("batch_before_merge", batch);        
        
        if(isPEExist){
            newPe.put("potEmpDuplId", newBatchId);
            
            String peMycoids = existingPEMycoidList.stream()
                    .map(map -> map.getOrDefault("existingEmpId",""))
                    .collect(Collectors.joining("','", "'", "'"));
            
            String peIds = existingPEMycoidList.stream()
                    .map(map -> map.getOrDefault("existingPotEmpId",""))
                    .collect(Collectors.joining("','", "'", "'"));
            
            msg("Existing PE ID => "+peIds);
            
            db.update(
                    "UPDATE app_fd_empm_pe_potEmp p "
//                    + "INNER JOIN app_fd_empm_reg r ON r.id = p.c_emp_fk "
                    + "SET p.c_potEmpDuplId = '"+newBatchId+"', p.c_isPotEmp_remarks = 'Duplicate Potential Employer' "
                    + "WHERE p.id IN ("+peIds+") "
            );
            
            pe_need_action++;
        }else if(isTPEExist){
            newPe.put("isPotEmp", "N");
            newPe.put("isPotEmp_remarks", "MycoID exists in DB. Status: "+existMycoidStatus);
            newPe.put("status", Constants.STATUS.POT_EMP.POTENTIAL_REJECTED);
            pe_failed++;
        }else{
            pe_inserted++;
        }

        String peId = CommonUtils.saveUpdateForm2(Constants.APP_ID.EMPM, "pot_emp_data", "", newPe);
        
        db.update("UPDATE "+Constants.TABLE.POT_EMP_UPLOAD_DATA+" "
                + "SET c_isPotEmp = ? WHERE id = ?",
                new String[]{"Y"},
                new String[]{tempPeId}
        );
    }

    private boolean alreadyInPE(DBHandler db, String batch, String empFk) {
        String query = "SELECT * FROM app_fd_empm_pe_potEmp WHERE "
                + "c_emp_fk = ? and c_batch = ?";
        HashMap qHm = db.selectOneRecord(query, new String[]{empFk, batch});
        
        if(qHm != null){
            return true;
        }
        
        return false;
    }

    //To check if exits in PE
    private boolean checkMycoidExistAsPE(DBHandler db, String batch, String empFk) {
        String query = "SELECT * FROM app_fd_empm_pe_potEmp WHERE "
                + "c_emp_fk = ? and c_batch = ?";
        HashMap qHm = db.selectOneRecord(query, new String[]{empFk, batch});

        if(qHm != null){
            return true;
        }

        return false;
    }
    
    public void handleFlushOut(DBHandler db, String peId, String empId){
        
        String batchId = db.selectOneValueFromId(Constants.TABLE.POT_EMP,
                "c_batch", peId);
        
        String sql = "DELETE FROM app_fd_empm_pe_potEmp WHERE id = ?";
        updateCount += db.delete(sql, new String[]{peId});

        sql = "DELETE FROM app_fd_empm_reg WHERE id = ?";
        db.delete(sql, new String[]{empId});
        
        sql = "SELECT * FROM "+Constants.TABLE.POT_EMP+" "
                + "WHERE c_batch = ?";        
        //check if batch is empty
        ArrayList bList = db.select(sql, new String[]{batchId});
        if(bList.size()==0){
            sql = "DELETE FROM "+Constants.TABLE.POT_EMP_UPLOAD+" WHERE id = ?";
            db.delete(sql, new String[]{batchId});
        }
    }
    
    
    private String handleSubBatch(DBHandler db, String[] ids) {
        
        String refno = getRefPrefix(db)
                        +"/Sub/"
                        +CommonUtils.get_DT_CurrentDateTime("YYYY")
                        +"/"
                        +CommonUtils.getRefNo("6", 
                                Constants.ENV_VAR.POT_EMP.WRITE_OFF_COUNTER);
        
        HashMap subBatchHm = new HashMap();
        subBatchHm.put("upl_status", "SubBatch");
        subBatchHm.put("batch", refno);
        
        String batchId = CommonUtils.saveUpdateForm2("", Constants.FORM_ID.POTEMP_FILE_UPL, "", subBatchHm);
        
        for(String id:ids){
            String query = "UPDATE app_fd_empm_pe_potEmp SET c_batch = ?  where id = ?";        
            count += db.update(query, new String[]{batchId }, new String[]{id});
        }
        
        return refno;
    }
    
    private String getRefPrefix(DBHandler db) {
        String prefix = "";
        HashMap hm = db.selectOneRecord("SELECT c_pe_batch_refCode FROM app_fd_empm_reg_stp WHERE id = ?", 
                new String[]{Constants.DATA_ID.MAIN_SETUP_ID});

        prefix = hm!=null?hm.get("c_pe_batch_refCode").toString():"";
        
        return prefix;
    }
    
    private static String getWORefPrefix(DBHandler db) {
        String prefix = "";
        HashMap hm = db.selectOneRecord("SELECT c_pe_wo_refCode FROM app_fd_empm_reg_stp WHERE id = ?", 
                new String[]{Constants.DATA_ID.MAIN_SETUP_ID});

        prefix = hm!=null?hm.get("c_pe_wo_refCode").toString():"";
        
        return prefix;
    }

    public static String getWriteOffId(DBHandler db) {
        
        String refno = getWORefPrefix(db)
                        +"/"
                        +CommonUtils.get_DT_CurrentDateTime("YYYY")
                        +"/"
                        +CommonUtils.getRefNo("6", 
                                Constants.ENV_VAR.POT_EMP.WRITE_OFF_COUNTER);
        
        HashMap hm = new HashMap();        
        hm.put("status", "Pending Write-Off Approval");                
        hm.put("ref_no", refno);                
        return CommonUtils.saveUpdateForm2("", Constants.FORM_ID.POTEMP_WRITEOFF, "", hm);
    }
    
    private void handleWriteOff(DBHandler db, String potemp_id, String writeOffId){
        String sql = "UPDATE "+Constants.TABLE.POT_EMP 
                + " SET c_status = ?, c_writeoff_fk = ? "
                + " WHERE id = ? ";
        
        updateCount += db.update(sql, 
                new String[]{Constants.STATUS.POT_EMP.PENDING_WRITE_OFF, 
                                writeOffId}, 
                new String[]{potemp_id});
        
        HashMap audHm = new HashMap();
        audHm.put("status", "Submitted for Write-Off");
        audHm.put("fk", writeOffId);
        audHm.put("createdByName", WorkflowUtil.getCurrentUserFullName());
        
        CommonUtils.saveUpdateForm2(Constants.APP_ID.EMPM, 
                                                    Constants.FORM_ID.AUDIT_TRAIL, 
                                                    "", audHm);
    }
    
//    private void processPE(DBHandler db, String peId, String btnType) {
//        ArrayList<HashMap<String, String>> pList = isIDBatch(db, peId);
//        LogUtil.info(this.getClassName(), pList.toString());
//        String empId = "";
//        
//        for(HashMap pe:pList){
//            peId = pe.get("id")==null?"":pe.get("id").toString();
//            empId = pe.get("c_emp_fk")==null?"":pe.get("c_emp_fk").toString();
//            setData(db, peId,empId, btnType);
//        }
//    }
    
    private ArrayList<HashMap<String, String>> isIDBatch(DBHandler db, String id){
        String sql = "SELECT * FROM "+Constants.TABLE.POT_EMP+" WHERE c_batch = ?";
        ArrayList<HashMap<String, String>> list = db.select(sql, new String[]{id});
        
        if(list.isEmpty()){
            sql = "SELECT * FROM "+Constants.TABLE.POT_EMP+" WHERE id = ?";
            list = db.select(sql, new String[]{id});
        }
        
        return list;
    }
    
//    private void setData(DBHandler db, String peId, String empId, String btnType) {
//        switch(btnType){
//            case "complaint":
//                sendToEnforcement(db, peId, empId);
//            break;
//            case "add_to_truePE":
//                handleImportToTrue(db, peId, empId);                
//            break;
//            case "add_to_dirtyList":
//                handleImportToDirty(db, peId, empId);
//            break;
//        }
//        
//    }
    
    private void sendToEnforcement(DBHandler db, String peId, String empId) {
        String sql = "UPDATE "+Constants.TABLE.POT_EMP+" SET c_status = ? WHERE id = ?";
        int i = db.update(sql, new String[]{Constants.STATUS.POT_EMP.ENFORCEMENT},
                new String[]{peId});
        
        sql = "UPDATE "+Constants.TABLE.EMPREG+" SET c_last_move = ? WHERE id = ?";
        i = db.update(sql, 
                new String[]{
                    Constants.LAST_MOVEMENT.COMPLAINT_TO_ENFORCEMENT
                },
                new String[]{empId});
        
        updateCount++;
    }
    
    private void handleImportToDirty(DBHandler db, String peId, String empId) {
        
        String sql = "UPDATE "+Constants.TABLE.POT_EMP+" SET c_status = ? WHERE id = ?";
        int i = db.update(sql, new String[]{Constants.STATUS.POT_EMP.DIRTY},
                new String[]{peId});
        
        sql = "UPDATE "+Constants.TABLE.EMPREG+" SET c_data_status = ?,c_last_move = ? WHERE id = ?";
        i = db.update(sql, 
                new String[]{
                    Constants.STATUS.EMP.DIRTY_LISTED,
                    Constants.LAST_MOVEMENT.DIRTY_LIST
                },
                new String[]{empId});
        
        if(i>0){ 
            updateCount++; 
        }else{
            failedCount++;
        }
    }
    
    private void handleImportToTrue(DBHandler db, String peId, String empId) {
        
        EmpmObj emp = new EmpmObj(db, EmpmObj.BY_ID, empId);
        CriteriaUtil cut = new CriteriaUtil(db);
        
        String query = "";
        int countIn = 0;
        
        if(cut.isPassCriteria(emp)){
            
            query = "UPDATE app_fd_empm_pe_potEmp SET c_status = ? WHERE id = ?";
            countIn = db.update(query, 
                        new String[]{Constants.STATUS.POT_EMP.TRUE},
                        new String[]{peId});

            query = "UPDATE app_fd_empm_reg SET c_data_status= ?, c_last_move = ? WHERE id = ? ";
            countIn = db.update(query, 
                        new String[]{Constants.STATUS.EMP.TRUE_POTENTIAL_EMPLOYER,
                        Constants.LAST_MOVEMENT.TRUE_POTENTIAL},
                        new String[]{empId});
            updateCount+=countIn;
        }else{   
            
            query = "UPDATE app_fd_empm_pe_potEmp SET c_status = ?, c_dismissal_reason = '"+cut.getMessage()+"' WHERE id = ?";
            countIn = db.update(query, 
                        new String[]{Constants.STATUS.POT_EMP.DISMISS},
                        new String[]{peId});

            query = "UPDATE app_fd_empm_reg SET c_data_status= ?, c_last_move=? WHERE id = ? ";
            countIn = db.update(query, 
                                    new String[]{
                                        Constants.STATUS.EMP.POTENTIAL_EMPLOYER,
                                        Constants.LAST_MOVEMENT.POTENTIAL
                                    },
                                    new String[]{empId}
                                );
            failedCount+=countIn;
        }       
    }

    private void startWriteOffProcess(String writeOffId) {
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        Long appVersion = appService.getPublishedVersion(Constants.APP_ID.EMPM);
        //get process
        WorkflowProcess process = appService.getWorkflowProcessForApp(Constants.APP_ID.EMPM, 
                appVersion.toString(), "pe_write_off");

        WorkflowUserManager wum = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
        String username = wum.getCurrentUsername();
        //start process
        workflowManager.processStart(process.getId(), null, null, username, writeOffId, false);
        
    }
    
    // VV UPLOADED TO PE --------------------------------------------------

//    private ArrayList<HashMap<String, String>> isIdUplBatch(DBHandler db, String id) {
//
//        String query = "SELECT * from app_fd_empm_pe_upl_data u where u.c_batch = ? "
//                        + "AND c_status = 'SUCCESS' "
//                        + "AND (c_isPotEmp is null OR c_isPotEmp='false')";
//        ArrayList<HashMap<String, String>> qList = db.select(query, new String[]{id});
//        
//        if(qList.isEmpty()){
//            LogUtil.info("not batch id",id);
//            query = "SELECT * from app_fd_empm_pe_upl_data u where id = ? "
//                        + "AND c_status = 'SUCCESS' "
//                        + "AND (c_isPotEmp is null OR c_isPotEmp='false')";
//            
//            qList = db.select(query, new String[]{id});
//        }else{
//            LogUtil.info("batch id",id);
//        }
//        
//        return qList;
//    }
    
    private ArrayList<HashMap<String, String>> getAllUploadedSuccessItem(DBHandler db, String id) {

        String query = "SELECT d.c_batch as batch_name, r.c_mycoid as mycoid, u.* from "
                + " app_fd_empm_pe_upl_data u  "
                        + "INNER JOIN app_fd_empm_pe_file_upl d ON d.id = u.c_batch "
                + "INNER JOIN app_fd_empm_reg_temp r ON r.id = u.c_emp_fk " 
                        + "where u.c_batch = ? "
                        + "AND u.c_status = 'SUCCESS' "
                        + "AND (u.c_isPotEmp is null OR u.c_isPotEmp='false' OR u.c_isPotEmp = 'N')";
        ArrayList<HashMap<String, String>> qList = db.select(query, new String[]{id});
        
        if(qList.isEmpty()){
//            LogUtil.info("not batch id",id);
            query = "SELECT d.c_batch as batch_name, r.c_mycoid as mycoid,u.* from "
                    + "app_fd_empm_pe_upl_data u "
                        + "INNER JOIN app_fd_empm_pe_file_upl d ON d.id = u.c_batch "
                    + "INNER JOIN app_fd_empm_reg_temp r ON r.id = u.c_emp_fk " 
                        + "where u.id = ? "
                        + "AND u.c_status = 'SUCCESS' "
                        + "AND (u.c_isPotEmp is null OR u.c_isPotEmp='false' OR u.c_isPotEmp = 'N')";
            
            qList = db.select(query, new String[]{id});
        }else{
            LogUtil.info("batch id",id);
        }
        
        return qList;
    }
    
    // VV FLUSH OUT UPLOADED BATCH --------------------------------------------------

    private ArrayList<HashMap<String, String>> getAllUploadedItem(DBHandler db, String id) {
        ArrayList<HashMap<String, String>> pEmpl  = db.select(
                "SELECT * FROM app_fd_empm_pe_upl_data p WHERE c_batch = ?",
                new String[]{id}
        );
        
        db.update(
                "UPDATE "+Constants.TABLE.EMPREG_TEMP+" r "
                        + "JOIN "+Constants.TABLE.POT_EMP_UPLOAD_DATA+" p ON p.c_emp_fk = r.id "
                        + "SET r.c_mycoid = CONCAT(r.c_mycoid,'_DELETED'),r.c_comp_name = CONCAT(r.c_comp_name,'_DELETED') "
                        + "WHERE p.c_batch = '"+id+"'"
        );
        
        return pEmpl;
    }

    private void handleFlushOutUploadedItem(DBHandler db, String uplItemId, String empId) {
        
        int del = db.delete(
            "DELETE FROM "+Constants.TABLE.POT_EMP_UPLOAD_DATA+" WHERE id = ? ",
            new String[]{uplItemId}
        );
        if(del>0){
            del = db.delete(
                "DELETE FROM "+Constants.TABLE.POT_EMP_EMPREG_TEMP+" WHERE id = ? ",
                new String[]{empId}
            );
        }        
        itemCount++;
    }
    
    // --------------------------------------------------------------------------

    private String handleSelectPE(DBHandler db, String id) {
        msg("pe selected id "+id);
        HashMap peData = db.selectOneRecord(
                "SELECT r.c_mycoid, pe.c_batch FROM app_fd_empm_reg r INNER JOIN "
                        + "app_fd_empm_pe_potEmp pe ON pe.c_emp_fk = r.id "
                        + "WHERE pe.id = ? LIMIT 1 ", 
                        new String[]{id}
        );
        
        String mycoid = peData!=null?peData.getOrDefault("c_mycoid", "").toString():"";
        String batchId = peData!=null?peData.getOrDefault("c_batch", "").toString():"";
        
        int rejected = db.update(
                "UPDATE app_fd_empm_pe_potEmp pe "
                        + "INNER JOIN app_fd_empm_reg r ON r.id = pe.c_emp_fk "
                        + "SET c_status = ?, c_batch = c_potEmpDuplId,c_potEmpDuplId = ?, c_isPotEmp_remarks = ?, c_isPotEmp = ?  "
                        + "WHERE pe.id!=? AND r.c_mycoid = ? AND pe.c_potEmpDuplId = ?",
                new String[]{Constants.STATUS.POT_EMP.POTENTIAL_REJECTED, "", "Rejected in favor of other duplicate potential", "N"},
                new String[]{id, mycoid, batchId}
        );
        
        int selected = db.update(
                "UPDATE app_fd_empm_pe_potEmp "
                        + "SET c_batch = c_potEmpDuplId,c_potEmpDuplId = ?,  c_isPotEmp = ? "
                        + "WHERE id=?",
                new String[]{"", "Y"},
                new String[]{id}
        );
        
        return Integer.toString(selected)+" PE Selected, "+Integer.toString(rejected)+" PEs Rejected";
    }

    int BATCH_DELETED = 0;
    int PE_DATA_DELETED = 0;
    private String handleFlushOutNEW(DBHandler db, String batchId) {
        db.delete(
            "DELETE app_fd_empm_reg_temp "
                    + "FROM app_fd_empm_reg_temp "
                    + " JOIN app_fd_empm_pe_upl_data ON app_fd_empm_reg_temp.id = app_fd_empm_pe_upl_data.c_emp_fk "
                    + "WHERE app_fd_empm_pe_upl_data.c_batch = ? ", 
            new String[]{batchId}
        );
        
        db.delete(
            "DELETE FROM app_fd_empm_pe_upl_data WHERE c_batch = ? ", 
            new String[]{batchId}
        );
        
        PE_DATA_DELETED += db.delete(
            "DELETE app_fd_empm_reg "
                    + "FROM app_fd_empm_reg  "
                    + " JOIN app_fd_empm_pe_potEmp ON app_fd_empm_reg.id = app_fd_empm_pe_potEmp.c_emp_fk "
                    + "WHERE app_fd_empm_pe_potEmp.c_batch = ? ", 
            new String[]{batchId}
        );
        
        db.delete(
            "DELETE FROM app_fd_empm_pe_potEmp WHERE c_batch = ? ", 
            new String[]{batchId}
        );
        
        BATCH_DELETED += db.delete(
            "DELETE FROM app_fd_empm_pe_file_upl WHERE id = ? ", 
            new String[]{batchId}
        );
        
        return Integer.toString(PE_DATA_DELETED)+" PE Records From "+Integer.toString(BATCH_DELETED)+" Batches";
    }
    
}
