package org.tools.webfilesync;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncFileRepository  extends JpaRepository<SyncFile, Long> {

	SyncFile findOneByPath(String path);
}
