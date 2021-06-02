package org.tools.webfilesync;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import lombok.Data;

@Component
@Scope("prototype")
@Data
public class Uploader extends Thread {
	
	@Autowired
	private SyncFileRepository repo;
	
	private String base64creds;
	private SyncFile uf;
	private Integer verbose;
	private String fileurl;		
	
	/* public Uploader(String base64creds, SyncFile uf, Integer verbose, String fileurl, SyncFileRepository repo) {
		super();
		this.base64creds = base64creds;
		this.uf = uf;
		this.verbose = verbose;
		this.fileurl = fileurl;
		this.repo = repo;
	} */
	
	@Transactional
	public void run() {
		RestTemplate restTemplate = new RestTemplate();
	    HttpHeaders headers = new HttpHeaders();			    
	    headers.add("authorization", "Basic " + base64creds);
	    File curfile = new File(uf.getPath());
	    if(curfile.isFile()) {
	    	if(verbose>1) {
				System.out.println("Uploading file on server:" + curfile.getName());
			}
	    	headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		    body.add("file", new FileSystemResource(new File(uf.getPath())));
		    body.add("rel_path", uf.getRelPath());
		    if(verbose>1) {
				System.out.println("Setting up request");
			}
		    HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
		    if(verbose>1) {
				System.out.println("Done setup");
			}
		    try {
			    ResponseEntity<String> response = restTemplate.postForEntity(fileurl, request , String.class);
			    if(verbose>1) {
					System.out.println("Finished uploading " + curfile.getName() + " with status:" + response.toString());
				}
		    }
		    catch(RestClientException e) {
		    	System.out.println("Error uploading file:");
		    	System.out.println(e);
		    }
	    }
	    else if(curfile.isDirectory()) {
	    	if(verbose>1) {
				System.out.println("Uploading directory on server:" + curfile.getName());
			}
	    	MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
		    map.add("rel_path", uf.getRelPath());
		    map.add("filename", uf.getName());
		    map.add("op", "mkdir");				    
		    
		    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

		    try {
		    	ResponseEntity<String> response = restTemplate.postForEntity(fileurl, request , String.class);
		    }
		    catch(RestClientException e) {
		    	System.out.println("Error uploading file:");
		    	System.out.println(e);
		    }
	    }
	    if(verbose>1) {
			System.out.println("Will now set database after request for ID:" + String.valueOf(uf.getId()));
		}
	    Optional<SyncFile> cfile = repo.findById(uf.getId());
	    if(verbose>1) {
			System.out.println("After find ID");
		}
	    if(cfile!=null) {
	    	if(verbose>1) {
				System.out.println("It's not a null");
			}
	    	SyncFile dd = cfile.get();
	    	if(dd!=null) {
		    	if(verbose>1) {
					System.out.println("Changing to no-op:" + dd.getName());
				}
		    	dd.setOp("no-op");
		    	repo.save(dd);
	    	}
	    	else {
			    if(verbose>1) {
					System.out.println("ID not found here: " + String.valueOf(uf.getId()));
				}
		    }
	    }
	    else {
		    if(verbose>1) {
				System.out.println("ID not found: " + String.valueOf(uf.getId()));
			}
	    }
	}
}
