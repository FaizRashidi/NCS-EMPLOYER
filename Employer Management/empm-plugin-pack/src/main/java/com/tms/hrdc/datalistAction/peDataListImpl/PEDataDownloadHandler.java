/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tms.hrdc.datalistAction.peDataListImpl;

import com.tms.hrdc.util.DBHandler;

/**
 *
 * @author faizr
 */
public class PEDataDownloadHandler {
    
    DBHandler db = new DBHandler();
    String[] data = {};
    
    public PEDataDownloadHandler(DBHandler db, String[] data){
        this.db = db;
        this.data = data;
    }
    
}
