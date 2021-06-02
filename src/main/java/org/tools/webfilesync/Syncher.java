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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.Data;

@Component
@Scope("prototype")
@Data
public class Syncher extends Thread {
	
	@Autowired
	private SyncFileRepository repo;

	private String basefolder;
	private List<String> filters;
	private String relpath;
	private String fileurl;
	
	private Integer verbose;
	private Date updateDate;

	public static List<Path> listFiles(Path path) throws IOException {

		List<Path> result;
		try (Stream<Path> walk = Files.walk(path)) {
			result = walk.filter(Files::isRegularFile)
					.collect(Collectors.toList());
		}
		return result;

	}

	/* public Syncher(String basefolder, List<String> filters, String relpath, String fileurl,
			Integer verbose, Date updateDate) {
		super();
		this.basefolder = basefolder;
		this.filters = filters;		
		this.relpath = relpath;
		this.fileurl = fileurl;		
		this.verbose = verbose;
		this.updateDate = updateDate;
		ApplicationContext context = new AnnotationConfigApplicationContext(Syncher.class);
		this.repo = context.getBean(SyncFileRepository.class);
	} */

	@Transactional
	public void run() {
		System.out.println("Running thread sync for basefolder:" + basefolder);		

		File curpath = new File(basefolder);
		String[] filenames = curpath.list();			
		for (String filename:filenames) {
			if(verbose>1) {
				System.out.println("Processing filename: " + filename + " from folder: " + relpath);
			}
			String absolutepath = basefolder + "/" + filename;
			String folderpath = basefolder;
			Long lastModified = new File(absolutepath).lastModified();

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
							prevfile.setSize(Files.size(Paths.get(absolutepath)));
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
						sfile.setSize(Files.size(Paths.get(absolutepath)));						
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					try {
						repo.save(sfile);		
						if(verbose>1) {
							System.out.println("Saved new record");
						}
					} catch (Exception e) {
						System.out.println("Error saving");
						e.printStackTrace();
					}
				}
			}
			else {
				if(verbose>1) {
					System.out.println("File not valid");
				}
			}
		}
	}
}
