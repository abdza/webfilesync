package org.tools.webfilesync;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
	
	public void synclocalfile(String basefolder) {			    
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
					else {
						prevfile.setOp("no-op");
					}
					prevfile.setLastChecked(new Date());
					repo.save(prevfile);
				}
				else {
					SyncFile sfile = new SyncFile();					
					sfile.setName(x.getFileName().toString());
					sfile.setPath(x.toAbsolutePath().toString());					
					sfile.setFolderPath(x.getParent().toString());
					sfile.setRelPath(x.getParent().toString().substring(basefolder.length()));
					sfile.setLastUpdate(x.toFile().lastModified());
					sfile.setLastChecked(new Date());
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
	
	
	public void syncuploadfile(Long maxsize) {		
		
		List<SyncFile> ufiles = repo.findAllByOp("upload");
		String fileurl = "http://localhost:9010/api/file_manager/explore/downloads";
		ufiles.forEach(uf -> {			
			System.out.println("Sending " + uf.getName());
			if(uf.getSize()<maxsize) {
				RestTemplate restTemplate = new RestTemplate();
			    HttpHeaders headers = new HttpHeaders();
			    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
			    headers.add("authorization", "Basic YWRtaW46Y2JteWQzdiFAIw==");
			    
			    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
			    body.add("file", new FileSystemResource(new File(uf.getPath())));
			    body.add("rel_path", uf.getRelPath());
			    
			    // MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
			    //map.add("rel_path", "/hero/");
			    
			    HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
			    
			    ResponseEntity<String> response = restTemplate.postForEntity(
			    		fileurl, request , String.class);
			}
			else {
				System.out.println("FIle too big to send");
			}
		});
	}
	
	@PostConstruct
	public void init() {
		String basefolder = null;
		Long maxsize = (long) 50000000;
		
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
		
		if(basefolder!=null) {
			synclocalfile(basefolder);
			syncuploadfile(maxsize);
		}
	}
}
