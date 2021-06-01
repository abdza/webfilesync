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

public class Syncher extends Thread {

	private String basefolder;
	private List<String> filters;
	private String relpath;
	private String fileurl;
	private SyncFileRepository repo;
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

	public Syncher(String basefolder, List<String> filters, String relpath, String fileurl,
			SyncFileRepository repo, Integer verbose, Date updateDate) {
		super();
		this.basefolder = basefolder;
		this.filters = filters;		
		this.relpath = relpath;
		this.fileurl = fileurl;
		this.repo = repo;
		this.verbose = verbose;
		this.updateDate = updateDate;
	}

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
		}
	}
}
