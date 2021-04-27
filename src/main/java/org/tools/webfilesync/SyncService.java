package org.tools.webfilesync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SyncService {
	
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
	
	public void syncfile(String basefolder) {
		System.out.println("Syncing " + basefolder);
		Path path = Paths.get(basefolder);
		List<Path> paths;
		try {
			paths = listFiles(path);
			paths.forEach(x -> System.out.println(x));			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}        
	}
	
	@PostConstruct
	public void init() {
		String basefolder = ".";
		
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
		syncfile(basefolder);
	}
}
