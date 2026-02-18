package com.api.test.api_verifier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
public class TestSuiteService {

	private static final String BASE_PATH = System.getProperty("user.home") + "/testiq/testcases/";
	private final ObjectMapper mapper = new ObjectMapper();

	public Map<String, Object> getAllSuites() {
		File root = new File(BASE_PATH);
		if (!root.exists())
			root.mkdirs();
		return buildTree(root);
	}

	private Map<String, Object> buildTree(File dir) {
		Map<String, Object> node = new LinkedHashMap<>();

		List<String> orderList = loadOrderList(dir);

		File[] children = Objects.requireNonNull(dir.listFiles());
		List<File> folderFiles = new ArrayList<>();
		List<File> testFiles = new ArrayList<>();

		for (File f : children) {
			if (f.isDirectory())
				folderFiles.add(f);
			else if (f.getName().endsWith(".json"))
				testFiles.add(f);
		}

		folderFiles.sort((a, b) -> compareByOrder(a.getName(), b.getName(), orderList));
		testFiles.sort((a, b) -> compareByOrder(a.getName(), b.getName(), orderList));

		Map<String, Object> folders = new LinkedHashMap<>();
		for (File f : folderFiles)
			folders.put(f.getName(), buildTree(f));

		List<String> tests = new ArrayList<>();
		for (File f : testFiles)
			tests.add(f.getName());

		node.put("folders", folders);
		node.put("tests", tests);
		return node;
	}

	private List<String> loadOrderList(File dir) {
		File orderFile = new File(dir, "order.txt");
		if (!orderFile.exists())
			return List.of();
		try {
			return Files.readAllLines(orderFile.toPath()).stream().map(String::trim)
					.filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private int compareByOrder(String a, String b, List<String> order) {
		int idxA = order.indexOf(a);
		int idxB = order.indexOf(b);
		if (idxA == -1 && idxB == -1)
			return a.compareToIgnoreCase(b);
		if (idxA == -1)
			return 1;
		if (idxB == -1)
			return -1;
		return Integer.compare(idxA, idxB);
	}

	public Map<String, Object> readTest(String path) throws IOException {
		File f = new File(BASE_PATH + path);
		return f.exists() ? mapper.readValue(f, Map.class) : Map.of();
	}

	@SuppressWarnings("unchecked")
	public String createTest(String path, Map<String, Object> content) throws Exception {
		if (content == null)
			content = new LinkedHashMap<>();

		Object idObj = content.get("id");
		int idVal = -1;
		if (idObj instanceof Number) {
			idVal = ((Number) idObj).intValue();
		}
		if (idVal < 0) {
			int nextId = generateNextTestId();
			content.put("id", nextId);
		}

		File f = new File(BASE_PATH + path);
		f.getParentFile().mkdirs();
		mapper.writerWithDefaultPrettyPrinter().writeValue(f, content);

		updateParentRequiresFromChild(content);

		return "Created";
	}

	@SuppressWarnings("unchecked")
	public String updateTest(String path, Map<String, Object> content) throws Exception {
		File f = new File(BASE_PATH + path);
		if (!f.exists())
			return "Test Not Found";

		if (content == null)
			content = new LinkedHashMap<>();

		Object idObj = content.get("id");
		if (!(idObj instanceof Number)) {

			int nextId = generateNextTestId();
			content.put("id", nextId);
		}

		mapper.writerWithDefaultPrettyPrinter().writeValue(f, content);

		updateParentRequiresFromChild(content);

		return "Updated";
	}

	public String deleteTest(String path) {
		return new File(BASE_PATH + path).delete() ? "Deleted" : "Not Found";
	}

	public String renameTest(String oldPath, String newName) {
		File oldFile = new File(BASE_PATH + oldPath);
		if (!oldFile.exists())
			return "Test Not Found";

		if (!newName.endsWith(".json"))
			newName += ".json";

		File newFile = new File(oldFile.getParent(), newName);
		return oldFile.renameTo(newFile) ? "Renamed" : "Failed";
	}

	public String moveTest(String oldPath, String newPath) {
		try {
			File src = new File(BASE_PATH + oldPath);
			File dest = new File(BASE_PATH + newPath);
			dest.getParentFile().mkdirs();
			Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return "Moved";
		} catch (Exception e) {
			return "Error: " + e.getMessage();
		}
	}

	public String createFolder(String path) {
		return new File(BASE_PATH + path).mkdirs() ? "Folder Created" : "Already Exists";
	}

	public String renameFolder(String oldPath, String newName) {
		File oldFile = new File(BASE_PATH + oldPath);
		if (!oldFile.exists())
			return "Folder Not Found";

		File newFile = new File(oldFile.getParent(), newName);
		return oldFile.renameTo(newFile) ? "Folder Renamed" : "Failed";
	}

	public String deleteFolder(String path) {
		File dir = new File(BASE_PATH + path);
		if (!dir.exists())
			return "Not Found";

		deleteRecursive(dir);
		return "Folder Deleted";
	}

	private void deleteRecursive(File f) {
		if (f.isDirectory())
			for (File c : Objects.requireNonNull(f.listFiles()))
				deleteRecursive(c);
		f.delete();
	}

	private int generateNextTestId() {
		File root = new File(BASE_PATH);
		if (!root.exists()) {
			root.mkdirs();
			return 100;
		}
		return findMaxIdInDir(root) + 1;
	}

	@SuppressWarnings("unchecked")
	private int findMaxIdInDir(File dir) {
		int maxId = 99;

		File[] children = dir.listFiles();
		if (children == null)
			return maxId;

		for (File f : children) {
			if (f.isDirectory()) {
				maxId = Math.max(maxId, findMaxIdInDir(f));
			} else if (f.getName().endsWith(".json")) {
				try {
					Map<String, Object> json = mapper.readValue(f, Map.class);
					Object idObj = json.get("id");
					if (idObj instanceof Number) {
						int id = ((Number) idObj).intValue();
						if (id > maxId)
							maxId = id;
					}
				} catch (Exception ignored) {
				}
			}
		}
		return maxId;
	}

	@SuppressWarnings("unchecked")
	private void updateParentRequiresFromChild(Map<String, Object> childContent) {
		if (childContent == null)
			return;

		Object parentIdObj = childContent.get("parentId");
		if (parentIdObj == null)
			return;

		Object requiresObj = childContent.get("requires");
		if (!(requiresObj instanceof List)) {
			return;
		}
		List<Map<String, Object>> childRequires;
		try {
			childRequires = (List<Map<String, Object>>) requiresObj;
		} catch (ClassCastException e) {
			return;
		}
		if (childRequires.isEmpty())
			return;

		List<Integer> parentIds = new ArrayList<>();

		if (parentIdObj instanceof Number) {
			parentIds.add(((Number) parentIdObj).intValue());
		} else if (parentIdObj instanceof List) {
			for (Object o : (List<?>) parentIdObj) {
				if (o instanceof Number) {
					parentIds.add(((Number) o).intValue());
				}
			}
		} else {
			return;
		}

		for (Integer pid : parentIds) {
			if (pid == null)
				continue;
			mergeRequiresIntoParent(pid, childRequires);
		}
	}

	@SuppressWarnings("unchecked")
	private void mergeRequiresIntoParent(int parentId, List<Map<String, Object>> childRequires) {
		File root = new File(BASE_PATH);
		if (!root.exists())
			return;

		File parentFile = findTestFileById(root, parentId);
		if (parentFile == null)
			return;

		try {
			Map<String, Object> parentJson = mapper.readValue(parentFile, Map.class);

			Object existingReqObj = parentJson.get("requires");
			List<Map<String, Object>> existingRequires;
			if (existingReqObj instanceof List) {
				existingRequires = (List<Map<String, Object>>) existingReqObj;
			} else {
				existingRequires = new ArrayList<>();
			}

			Set<String> existingNames = new HashSet<>();
			for (Map<String, Object> r : existingRequires) {
				Object n = r.get("name");
				if (n != null)
					existingNames.add(n.toString());
			}

			for (Map<String, Object> cr : childRequires) {
				Object nameObj = cr.get("name");
				Object pathObj = cr.get("path");
				if (nameObj == null || pathObj == null)
					continue;

				String name = nameObj.toString();
				if (existingNames.contains(name)) {
					continue;
				}

				Map<String, Object> toAdd = new LinkedHashMap<>();
				toAdd.put("name", name);
				toAdd.put("path", pathObj.toString());
				existingRequires.add(toAdd);
				existingNames.add(name);
			}

			if (!existingRequires.isEmpty()) {
				parentJson.put("requires", existingRequires);
				mapper.writerWithDefaultPrettyPrinter().writeValue(parentFile, parentJson);
			}
		} catch (Exception ignored) {
		}
	}

	@SuppressWarnings("unchecked")
	private File findTestFileById(File dir, int targetId) {
		File[] children = dir.listFiles();
		if (children == null)
			return null;

		for (File f : children) {
			if (f.isDirectory()) {
				File found = findTestFileById(f, targetId);
				if (found != null)
					return found;
			} else if (f.getName().endsWith(".json")) {
				try {
					Map<String, Object> json = mapper.readValue(f, Map.class);
					Object idObj = json.get("id");
					if (idObj instanceof Number) {
						int id = ((Number) idObj).intValue();
						if (id == targetId)
							return f;
					}
				} catch (Exception ignored) {
				}
			}
		}
		return null;
	}

}
