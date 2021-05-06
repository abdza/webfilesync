package org.tools.webfilesync;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Date;
import java.util.List;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
	
	@Autowired
	private NamedParameterJdbcTemplate namedjdbctemplate;
	
	private MapSqlParameterSource paramsource;
	
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
	
	public void synclocalfile(Date updateDate, String basefolder) {			    
		System.out.println("Syncing " + basefolder);
		Path path = Paths.get(basefolder);
		List<Path> paths;
		try {
			paths = listFiles(path);
			paths.forEach(x -> {
				System.out.println(x);
				SyncFile prevfile = repo.findOneByPath(x.toAbsolutePath().toString());
				if(prevfile!=null) {
					System.out.println("File already exists :" + prevfile.getPath());
					if(prevfile.getLastUpdate()!=x.toFile().lastModified()) {
						System.out.println("File updated :" + String.valueOf(x.toFile().lastModified()));
						prevfile.setOp("upload");
						try {
							prevfile.setSize(Files.size(x));						
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					prevfile.setLastChecked(updateDate);
					repo.save(prevfile);
				}
				else {
					SyncFile sfile = new SyncFile();					
					sfile.setName(x.getFileName().toString());
					sfile.setPath(x.toAbsolutePath().toString());					
					sfile.setFolderPath(x.getParent().toString());
					sfile.setRelPath(x.getParent().toString().substring(basefolder.length()));
					sfile.setLastUpdate(x.toFile().lastModified());
					sfile.setLastChecked(updateDate);
					sfile.setOp("upload");
					
					try {
						sfile.setSize(Files.size(x));						
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					repo.save(sfile);
					
				}
			});			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void detectDeleted(Date updateDate) {
		List<SyncFile> ufiles = repo.findAllByLastCheckedNotAndOpNot(updateDate,"deleted");
		
		ufiles.forEach(uf -> {
			System.out.println("Checking file " + uf.getName());
			if(!uf.getOp().equals("deleted")) {				
				File curfile = new File(uf.getPath());
				if(!curfile.exists()) {
					uf.setOp("delete");
					uf.setLastChecked(updateDate);
					repo.save(uf);
				}
			}
		});
	}
	
	
	public void syncuploadfile(Date updateDate, String fileurl, String base64creds, Long maxsize) {		
		System.out.println("In syncupload");
		List<SyncFile> ufiles = repo.findAllByOp("upload");
		
		ufiles.forEach(uf -> {			
			System.out.println("Sending " + uf.getName());
			if(uf.getSize()<maxsize) {
				RestTemplate restTemplate = new RestTemplate();
			    HttpHeaders headers = new HttpHeaders();
			    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
			    headers.add("authorization", "Basic " + base64creds);
			    
			    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
			    body.add("file", new FileSystemResource(new File(uf.getPath())));
			    body.add("rel_path", uf.getRelPath());
			    
			    // MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
			    //map.add("rel_path", "/hero/");
			    
			    HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
			    
			    ResponseEntity<String> response = restTemplate.postForEntity(
			    		fileurl, request , String.class);
			    uf.setOp("no-op");
			    repo.save(uf);
			}
			else {
				System.out.println("File too big to send");
			}
		});
	}
	
	public void syncdeletefile(Date updateDate, String fileurl, String base64creds) {		
		System.out.println("In syncdelete");
		List<SyncFile> ufiles = repo.findAllByOp("delete");
		
		ufiles.forEach(uf -> {			
			System.out.println("Deleting " + uf.getName());
			
			RestTemplate restTemplate = new RestTemplate();
		    HttpHeaders headers = new HttpHeaders();
		    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		    headers.add("authorization", "Basic " + base64creds);
		    		    
		    MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
		    map.add("rel_path", uf.getRelPath());
		    map.add("filename", uf.getName());
		    map.add("op", "delete");
		    
		    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
		    
		    ResponseEntity<String> response = restTemplate.postForEntity(
		    		fileurl, request , String.class);
		    
		    uf.setOp("deleted");
		    repo.save(uf);			
		});
	}
	
	@PostConstruct
	public void init() {
		String basefolder = null;
		Long maxsize = (long) 50000000;
		String fileurl = "http://localhost:9010/api/file_manager/explore/downloads";
		Date updateDate = new Date();
		
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
		//byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
		String base64Creds = new String(base64CredsBytes);
		
		if(basefolder!=null) {
			synclocalfile(updateDate,basefolder);
			syncuploadfile(updateDate,fileurl, base64Creds, maxsize);
			detectDeleted(updateDate);
			syncdeletefile(updateDate,fileurl, base64Creds);
		}
		System.out.println("Done it all");
		System.out.println(updateDate);
	}
}
