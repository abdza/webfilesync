package org.tools.webfilesync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class SyncService {
	
	@Autowired
	private SyncFileRepository repo;
	
	@Autowired
    private ApplicationArguments args;
	
	private Integer verbose; 
	private String fileurl;
	private Date updateDate;
	
	public static List<Path> listDirectories(Path path) throws IOException {

        List<Path> result;
        try (Stream<Path> walk = Files.walk(path)) {
            result = walk.filter(Files::isDirectory)
                    .collect(Collectors.toList());
        }
        return result;

    }
	
	public static List<Path> listFiles(Path path) throws IOException {

        List<Path> result;
        try (Stream<Path> walk = Files.walk(path)) {
            result = walk.filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        }
        return result;

    }
	
	public static List<Path> listAll(Path path) throws IOException {

        List<Path> result;
        try (Stream<Path> walk = Files.walk(path)) {
            result = walk
                    .collect(Collectors.toList());
        }
        return result;

    }
	
	public void synclocalfile(String basefolder,List<String> filters) {
		if(verbose>0) {
			System.out.println("Sync local file");
		}
		Path path = Paths.get(basefolder);
		List<Path> paths;
		try {
			paths = listAll(path);
			paths.forEach(x -> {				
				String relpath = "/";
				if(x.getParent().toString().length()>basefolder.length()) {
					relpath = x.getParent().toString().substring(basefolder.length());
				}
				if(verbose>1) {
					System.out.println("Sync file:" + x.getFileName() + " from:" + relpath);
				}
				String filename = x.getFileName().toString();
				String absolutepath = x.toAbsolutePath().toString();
				String folderpath = x.getParent().toString();
				Long lastModified = x.toFile().lastModified();
				SyncFile prevfile = repo.findOneByRelPathAndNameAndFileurl(relpath,filename,fileurl);
				boolean validfile = true;
				if(absolutepath.equals(basefolder)) {
					validfile = false;
				}
				if(filters.size()>0) {					
					for(String filter:filters) {						
						if(absolutepath.contains(filter)) {
							validfile = false;		
							if(verbose>1) {
								System.out.println("Fail because of filter:" + filter);
							}
						}
						else if(absolutepath.matches(filter)) {
							validfile = false;
							if(verbose>1) {
								System.out.println("Fail because of filter:" + filter);
							}
						}
					}
				}
				
				if(validfile) {
					if(verbose>1) {
						System.out.println("Is a valid file");
					}
					if(prevfile!=null) {
						if(verbose>1) {
							System.out.println("Record already exists");
						}
						if(!prevfile.getLastUpdate().equals(lastModified)) {
							if(verbose>1) {
								System.out.println("Record last update is different:" + String.valueOf(prevfile.getLastUpdate()) + " from file:" + String.valueOf(lastModified));
							}
							prevfile.setOp("upload");
							try {
								prevfile.setLastUpdate(lastModified);
								prevfile.setSize(Files.size(x));
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						prevfile.setLastChecked(updateDate);
						repo.save(prevfile);
						if(verbose>1) {
							System.out.println("Saved update");
						}
					}
					else {
						if(verbose>1) {
							System.out.println("Record does not exists");
						}
						SyncFile sfile = new SyncFile();					
						sfile.setName(filename);
						sfile.setPath(absolutepath);					
						sfile.setFolderPath(folderpath);
						sfile.setRelPath(relpath);
						sfile.setLastUpdate(lastModified);
						sfile.setLastChecked(updateDate);
						sfile.setOp("upload");
						sfile.setFileurl(fileurl);
						
						try {
							sfile.setSize(Files.size(x));						
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						repo.save(sfile);		
						if(verbose>1) {
							System.out.println("Saved new record");
						}
					}
				}
				else {
					if(verbose>1) {
						System.out.println("File not valid");
					}
				}
			});			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void detectDeleted() {
		if(verbose>0) {
			System.out.println("In detect delete file");
		}
		List<SyncFile> ufiles = repo.findAllByLastCheckedNotAndOpNotAndFileurl(updateDate,"deleted",fileurl);
		
		ufiles.forEach(uf -> {
			if(verbose>1) {
				System.out.println("Check deleting file:" + uf.getName());
			}
			if(!uf.getOp().equals("deleted")) {				
				File curfile = new File(uf.getPath());
				if(!curfile.exists()) {
					if(verbose>1) {
						System.out.println("File does not exists for:" + uf.getName());
					}
					uf.setOp("delete");
					uf.setLastChecked(updateDate);
					repo.save(uf);
				}
			}
		});
	}
	
	
	public void syncuploadfile(String base64creds, Long maxsize) {
		if(verbose>0) {
			System.out.println("In sync upload file");
		}
		List<SyncFile> ufiles = repo.findAllByOpAndFileurl("upload",fileurl);
		
		ufiles.forEach(uf -> {
			if(verbose>1) {
				System.out.println("Uploading on server: " + uf.getName());
			}
			if(uf.getSize()<maxsize) {
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
			else {
				System.out.println("File too big to send:" + uf.getName());
			}
		});
	}
	
	public void syncdeletefile(String base64creds) {
		if(verbose>0) {
			System.out.println("In sync delete file");
		}
		List<SyncFile> ufiles = repo.findAllByOpAndFileurl("delete",fileurl);
		
		ufiles.forEach(uf -> {
			if(verbose>1) {
				System.out.println("Deleting on server: " + uf.getName());
			}
			RestTemplate restTemplate = new RestTemplate();
		    HttpHeaders headers = new HttpHeaders();
		    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		    headers.add("authorization", "Basic " + base64creds);
		    		    
		    MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
		    map.add("rel_path", uf.getRelPath());
		    map.add("filename", uf.getName());
		    map.add("op", "delete");
		    
		    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
		    
		    ResponseEntity<String> response = restTemplate.postForEntity(fileurl, request , String.class);
		    
		    Optional<SyncFile> cfile = repo.findById(uf.getId());
		    if(cfile!=null) {				
		    	SyncFile dd = cfile.get();
		    	if(verbose>1) {
					System.out.println("Deleting in db: " + dd.getName());
				}
		    	repo.delete(dd);
		    }			
		});
	}
	
	@PostConstruct
	public void init() {
		String basefolder = null;
		Long maxsize = (long) 50000000;
		fileurl = "http://localhost:9010/api/file_manager/explore/downloads";
		updateDate = new Date();
		verbose = 0;
		List<String> filters = new ArrayList<String>();
		
		if(args.containsOption("basefolder")) 
        {
            //Get argument values
            List<String> values = args.getOptionValues("basefolder");
            try {	            	
				basefolder = values.get(0);				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
		
		if(args.containsOption("verbose")) 
        {
            //Get argument values
            List<String> values = args.getOptionValues("verbose");
            try {	            	
				verbose = Integer.valueOf(values.get(0));				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
		
		if(args.containsOption("fileurl")) 
        {
            //Get argument values
            List<String> values = args.getOptionValues("fileurl");
            try {	            	
				fileurl = values.get(0);				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
		
		if(args.containsOption("filtersfile")) 
        {
            //Get argument values
            List<String> values = args.getOptionValues("filtersfile");
            try {        	
				try (BufferedReader br = new BufferedReader(new FileReader(values.get(0)))) {
				    String line;
				    while ((line = br.readLine()) != null) {
				       // process the line.
				    	filters.add(line);
				    }
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
		
		if(args.containsOption("maxsize")) 
        {
            //Get argument values
            List<String> values = args.getOptionValues("maxsize");
            try {	            	
				maxsize = Long.valueOf(values.get(0));				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
		
		String plainCreds = "willie:p@ssword";
		
		if(args.containsOption("username") && args.containsOption("password")) 
        {
            //Get argument values
            List<String> unvalues = args.getOptionValues("username");
            List<String> passvalues = args.getOptionValues("password");
            try {	            	
				plainCreds = unvalues.get(0) + ":" + passvalues.get(0);	
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
		
		byte[] plainCredsBytes = plainCreds.getBytes();
		byte[] base64CredsBytes = Base64.getEncoder().encode(plainCredsBytes);		
		String base64Creds = new String(base64CredsBytes);
		
		if(basefolder!=null) {
			synclocalfile(basefolder,filters);
			syncuploadfile(base64Creds, maxsize);
			detectDeleted();
			syncdeletefile(base64Creds);
		}
	}
}
