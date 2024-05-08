/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tms.hrdc.datalistAction.peDataListImpl;

import com.tms.hrdc.util.CommonUtils;
import com.tms.hrdc.util.Constants;
import com.tms.hrdc.util.DBHandler;
import com.tms.hrdc.util.EmpUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.joget.commons.util.LogUtil;

/**
 *
 * @author faizr
 */
public class SetNewUplToPE {
    
    DBHandler db = new DBHandler();
    String[] data = {};
    String message;
    
    int PE_SUCCESS = 0;
    int PE_UNSUCCESS = 0;
    int PE_PENDING_ACTION = 0;
    
    public SetNewUplToPE(DBHandler db, String[] data){
        this.db = db;
        this.data = data;
    }
    
    private void msg(String msg){
        LogUtil.info(this.getClass().getName(), msg);
    }
    
    public String getReturnMessage(){
        return message;
    }
    
    public void handleNewToPE(){
        
        boolean isBatch = isBatch(data[0]);
        
        msg("Is batch?  "+Boolean.valueOf(isBatch));
        
        ArrayList<HashMap<String, String>> pList = new ArrayList();
        for(String id:data){
            pList.addAll(getPEList( id, isBatch));
        }
        
        msg("pe data "+pList.toString());
         
        String peType = "";
        String currBatchId = "";
        String currEmpId = "";
        String peId = "";
        String currBatchName = "";
        String mycoid = "";
        String comp_name = "";
        int counter = 0;
        
        String newBatchId = "";
        
        ArrayList mergedBatchId = new ArrayList();
        ArrayList mergedBatchNames = new ArrayList();
        
        for(HashMap pe:pList){

            currBatchId = pe.get("batch_id")==null?"":pe.get("batch_id").toString();
            currEmpId = pe.get("emp_id")==null?"":pe.get("emp_id").toString();
            peId = pe.get("pe_id")==null?"":pe.get("pe_id").toString();
            currBatchName = pe.get("batch_name")==null?"":pe.get("batch_name").toString();
            mycoid = pe.get("c_mycoid")==null?"":pe.get("c_mycoid").toString();
            comp_name = pe.get("c_comp_name")==null?"":pe.get("c_comp_name").toString();
            peType = pe.get("PE_TYPE")==null?"":pe.get("PE_TYPE").toString();

            msg("Batch - "+currBatchId
                    +" Name "+currBatchName+" peCount "
                    +Integer.toString(counter)
            +" Comp Name "+comp_name);

            if(!mergedBatchNames.contains(currBatchName)){
                mergedBatchNames.add(currBatchName.trim());
            }

            if(counter==0){
                newBatchId = currBatchId;
                msg("Batch - "+currBatchId+" Name "+currBatchName+" ==> SELECTED AS MAIN MERGED BATCH");
            }else {                            
                if(!currBatchId.equals(newBatchId) && !mergedBatchId.contains(currBatchId)){
                    mergedBatchId.add(currBatchId.trim());
                }                            
            }

            counter++;                        
            
            processNewPEDataByBatch(peType, peId, currBatchId, currEmpId, mycoid, newBatchId);
        }
        
        if(isBatch){      
            String mergedBatchIdStr = 
                    "'"+mergedBatchId.toString()
                            .replace("[", "")
                            .replace("]", "")
                            .replace(",", "','")+"'";

            String mergedBatchNamesStr = 
                            mergedBatchNames.toString()
                            .replace("[", "")
                            .replace("]", "");

            msg("mergedBatchId "+mergedBatchIdStr);
            msg("mergedBatchNamesStr "+mergedBatchNamesStr);

            //import if by pe
//            db.update(
//                    "UPDATE app_fd_empm_pe_potEmp SET c_batch = ? WHERE c_batch = ?",
//                    new String[]{newBatchId},
//                    new String[]{batch}
//            );

            //rename batch
            db.update("UPDATE app_fd_empm_pe_file_upl\n" +
                "SET c_isMerged = 'Y' " +
                "WHERE id IN ("+mergedBatchIdStr+")"
            );
        }
        
        message = "SUCCESS PE: "+Integer.toString(PE_SUCCESS)
                +", UNSUCCESS: "+Integer.toString(PE_UNSUCCESS)
                +", NEED ACTION: "+Integer.toString(PE_PENDING_ACTION);
    }
    
    
    
    private ArrayList<HashMap<String, String>> getPEList(String id, boolean isBatch) {
        
        String by = "";
        
        if(isBatch){
            by = " and d.c_batch = '"+id+"'";
        }else{
            by = " and d.id = '"+id+"'";
        }

        String query = 
                "select \n" +
                " 'NEW' as PE_TYPE, \n" +
                "d.c_batch as batch_id, d.id as pe_id, \n" +
                "r.id as emp_id, r.c_comp_name, r.c_mycoid,\n" +
                "u.c_batch as batch_name \n" +
                " from app_fd_empm_pe_upl_data d\n" +
                "INNER JOIN app_fd_empm_reg_temp r on r.id = d.c_emp_fk\n" +
                "INNER JOIN app_fd_empm_pe_file_upl u ON u.id = d.c_batch\n" +
                "WHERE d.c_status = 'SUCCESS'\n" +
                " and d.c_isPotEmp is null \n" + 
                by +
                "UNION\n" +
                "select " +
                " 'EXISTING' as PE_TYPE, \n" +
                "d.c_batch as batch_id, d.id as pe_id, \n" +
                "r.id as emp_id, r.c_comp_name, r.c_mycoid,\n" +
                "u.c_batch as batch_name\n" +
                "from app_fd_empm_pe_potEmp d\n" +
                "INNER JOIN app_fd_empm_reg r on r.id = d.c_emp_fk\n" +
                "INNER JOIN app_fd_empm_pe_file_upl u ON u.id = d.c_batch\n" +
                "WHERE d.c_status = 'POTENTIAL'\n" +
                " and d.c_isPotEmp = 'Y'\n" +
                by;
        ArrayList<HashMap<String, String>> qList = db.select(query);
        
        return qList;
    }

    private boolean isBatch(String id) {
        HashMap m = db.selectOneRecord("SELECT id FROM app_fd_empm_pe_file_upl WHERE id = ? ", new String[]{id});
        
        if(m!=null){
            return true;
        }else{
            return false;
        }
    }
    
    private void processNewPEDataByBatch(String peType, String peId, String currBatchId, String currEmpId, String mycoid, String newBatchId) {
        int countIn = 0;
        String query = "";
        ArrayList list = new ArrayList();
        boolean isMycoidExistAsPE = false;
        boolean override = false;
        boolean insert = false;

        msg("This pe mycoid ==> "+mycoid);
        
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
                "and r.c_mycoid = ?  " ,
                new String[]{mycoid}
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
        
        HashMap newPe = new HashMap();
        
        if(peType.equals("NEW")){
            
            db.update("UPDATE "+Constants.TABLE.POT_EMP_UPLOAD_DATA+" "
                    + "SET c_isPotEmp = ? WHERE id = ?",
                    new String[]{"Y"},
                    new String[]{peId}
            );
            
            currEmpId = EmpUtil.createPEEmployer(db, currEmpId, existingEmpId);
            peId = "";
            
            newPe.put("batch_before_merge", currBatchId);    
        }
        // create potential employer data
        
        newPe.put("emp_fk", currEmpId);
        newPe.put("batch", newBatchId);
        newPe.put("status", Constants.STATUS.POT_EMP.POTENTIAL);
        newPe.put("is_registered", "No");
        newPe.put("isPotEmp", "Y");                    
        
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
            
            PE_PENDING_ACTION++;
        }else if(isTPEExist){
            newPe.put("isPotEmp", "N");
            newPe.put("isPotEmp_remarks", "MycoID exists in DB. Status: "+existMycoidStatus);
            newPe.put("status", Constants.STATUS.POT_EMP.POTENTIAL_REJECTED);
            PE_UNSUCCESS++;
        }else{
            PE_SUCCESS++;
        }

        peId = CommonUtils.saveUpdateForm2(Constants.APP_ID.EMPM, "pot_emp_data", peId, newPe);
    }
}
