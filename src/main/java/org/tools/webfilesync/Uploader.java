package org.tools.webfilesync;

import java.io.File;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

public class Uploader extends Thread {
	
	private String base64creds;
	private SyncFile uf;
	private Integer verbose;
	private String fileurl;
	private SyncFileRepository repo;	
	
	public Uploader(String base64creds, SyncFile uf, Integer verbose, String fileurl, SyncFileRepository repo) {
		super();
		this.base64creds = base64creds;
		this.uf = uf;
		this.verbose = verbose;
		this.fileurl = fileurl;
		this.repo = repo;
	}
	
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
    
		    HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
		    
		    ResponseEntity<String> response = restTemplate.postForEntity(fileurl, request , String.class);
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
		    
		    ResponseEntity<String> response = restTemplate.postForEntity(fileurl, request , String.class);			    	
	    }
	    
	    Optional<SyncFile> cfile = repo.findById(uf.getId());
	    if(cfile!=null) {
	    	SyncFile dd = cfile.get();
	    	if(verbose>1) {
				System.out.println("Changing to no-op:" + dd.getName());
			}
	    	dd.setOp("no-op");
	    	repo.save(dd);
	    }
	}
}
