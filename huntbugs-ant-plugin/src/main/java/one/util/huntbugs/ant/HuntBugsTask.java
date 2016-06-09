/*
 * Copyright 2016 HuntBugs contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package one.util.huntbugs.ant;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import javax.xml.parsers.ParserConfigurationException;

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.HuntBugsResult;
import one.util.huntbugs.input.XmlReportReader;
import one.util.huntbugs.output.Reports;
import one.util.huntbugs.repo.AuxRepository;
import one.util.huntbugs.repo.CompositeRepository;
import one.util.huntbugs.repo.DirRepository;
import one.util.huntbugs.repo.JarRepository;
import one.util.huntbugs.repo.Repository;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.xml.sax.SAXException;

import com.strobel.assembler.metadata.ClasspathTypeLoader;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.JarTypeLoader;

public class HuntBugsTask extends Task {
	public enum LogLevel {
		QUIET, VERBOSE;
	}
	
	private Path classPath;
	
	private Path auxClassPath;
	
	private File xml;

	private File html;
	
	private File diff;
	
	private LogLevel log = LogLevel.VERBOSE; 
	
	@Override
	public void execute() throws BuildException {
		List<Repository> repos = createRepository();
		if(xml == null && html == null) {
			throw new BuildException("Either xml or html must be specified");
		}
		Repository repo = new CompositeRepository(repos);
		AnalysisOptions opt = new AnalysisOptions();
		Context ctx = new Context(repo, opt);
		if(log == LogLevel.VERBOSE)
			addListener(ctx);
		ctx.analyzePackage("");
		HuntBugsResult result = ctx;
		if(diff != null) {
			try {
				result = XmlReportReader.read(ctx, diff.toPath());
			} catch (IOException | SAXException | ParserConfigurationException e) {
				System.err.println("Unable to read old report "+diff+": "+e);
				System.err.println("Skipping diff generation");
			}
		}
		Reports.write(xml == null ? null : xml.toPath(), html == null ? null
				: html.toPath(), result);
	}

	private void addListener(Context ctx) {
		long[] lastPrint = {0};
        ctx.addListener((stepName, className, count, total) -> {
            if (count == total || System.currentTimeMillis() - lastPrint[0] > 2000) {
                System.err.println("HuntBugs: " + stepName + " [" + count + "/" + total + "]");
                lastPrint[0] = System.currentTimeMillis();
            }
            return true;
        });
	}

	private List<Repository> createRepository() {
		if(classPath == null || classPath.size() == 0) {
			throw new BuildException("Please specify classPath!");
		}
		List<Repository> repos = new ArrayList<>();
		for(String path : classPath.list()) {
			File file = new File(path);
			if(file.isDirectory()) {
				repos.add(new DirRepository(file.toPath()));
			} else if(file.isFile()) {
				try {
					repos.add(new JarRepository(new JarFile(file)));
				} catch (IOException e) {
					throw new BuildException(e);
				}
			} else {
				throw new BuildException("Class path element not found: "+path);
			}
		}
		if(auxClassPath != null) {
			List<ITypeLoader> auxLoaders = new ArrayList<>();
			for(String path : auxClassPath.list()) {
				File file = new File(path);
				if(file.isDirectory()) {
					auxLoaders.add(new ClasspathTypeLoader(file.toString()));
				} else if(file.isFile()) {
					try {
						auxLoaders.add(new JarTypeLoader(new JarFile(file)));
					} catch (IOException e) {
						throw new BuildException(e);
					}
				} else {
					throw new BuildException("Aux class path element not found: "+path);
				}
			}
			if(!auxLoaders.isEmpty()) {
				repos.add(new AuxRepository(new CompositeTypeLoader(auxLoaders.toArray(new ITypeLoader[0]))));
			}
		}
		return repos;
	}

	public void setClassPath(Path classPath) {
		if(this.classPath == null)
			this.classPath = new Path(getProject());
		this.classPath.append(classPath);
	}

	public void setAuxClassPath(Path auxClassPath) {
		if(this.auxClassPath == null)
			this.auxClassPath = new Path(getProject());
		this.auxClassPath.append(auxClassPath);
	}
	
	public void setDiff(File diff) {
		this.diff = diff;
	}

	public void setXml(File xml) {
		this.xml = xml;
	}

	public void setHtml(File html) {
		this.html = html;
	}
	
	public void setLog(LogLevel log) {
		this.log = log;
	}
}
