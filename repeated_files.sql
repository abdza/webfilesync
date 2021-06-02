SELECT count(id) AS num, name, rel_path, fileurl FROM SYNC_FILE GROUP BY name, rel_path, fileurl ORDER BY num DESC, rel_path
