package org.tools.webfilesync;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncFileRepository  extends JpaRepository<SyncFile, Long> {

	SyncFile findOneByPath(String path);
	List<SyncFile> findAllByOp(String op);
	List<SyncFile> findAllByLastCheckedNot(Date lastChecked);
}
