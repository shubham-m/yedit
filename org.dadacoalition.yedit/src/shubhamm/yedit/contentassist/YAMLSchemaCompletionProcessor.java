package shubhamm.yedit.contentassist;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.EditorSite;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.scanner.ScannerException;


@SuppressWarnings("rawtypes")
public class YAMLSchemaCompletionProcessor 
{

	private Map schema;
	private IncompleteYamlParser parser;
	private Yaml schemaParser;
	private String schemaPath;
	private BufferedReader reader;
    
	public YAMLSchemaCompletionProcessor()
	{
		schemaParser = new Yaml();
		schemaPath = "";
		schema = new HashMap();
	}
    
	public List<ICompletionProposal> computeCompletionProposalsFromSchema(ITextViewer viewer,int offset) 
	{
		List<ICompletionProposal> completionProposals = new ArrayList<ICompletionProposal>();
		try 
		{
			String extract = viewer.getDocument().get(0,offset);
			schema = readSchemaFile(extract);
			
			if(schema!=null)
			{
				parser = new IncompleteYamlParser();
				parser.construct(reader);
				Object extracted = parser.getConstructedYaml();
				
				boolean preMature = checkPrematureTermination(viewer, offset);
				
				if(!preMature)
				{
					extracted = normalizeExtracted(extracted);
					
					LinkedList path = parser.getPath();
					String leaf = parser.getLeaf();
					
//					path = normalizePath(path);
					
					setWorkbenchStatus(path+" "+leaf);
					
					completionProposals.addAll(getProposals(path.listIterator(),(Map) extracted,offset,getIndent(extract)));
				}
			}
			
		}
		catch (BadLocationException e) {
			e.printStackTrace();
		} 
		
		return completionProposals;
	}

	private boolean checkPrematureTermination(ITextViewer viewer, int offset)
	{
		boolean preMature = false;
		
		try
		{
			IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
			IFileEditorInput input = (IFileEditorInput)editor.getEditorInput() ;
			IFile file = input.getFile();

			file.deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
			
			if( parser.getLastException() != null)
			{
				int lineNo = parser.getLastException().getProblemMark().getLine() + 1;
				int lastOffset = viewer.getDocument().getLineInformation(lineNo).getOffset()+parser.getLastException().getProblemMark().getColumn();
				if(lastOffset<offset || parser.getLastException() instanceof ScannerException)
				{
					markError(file,parser.getLastException(),lastOffset);
					preMature = true;
				}
			}
		}
		catch (BadLocationException ex) {
			ex.printStackTrace();
		}
		catch (CoreException ex) {
			ex.printStackTrace();
		}
		
		return preMature;
	}

	private Object normalizeExtracted(Object extracted) 
	{
		if(extracted instanceof String)
		{
			extracted = new HashMap().put(extracted, null);
		}
		return extracted;
	}


	private void markError(IFile file,MarkedYAMLException exception, int lastOffset){
		System.out.println(exception.getProblem());
		try 
		{
			IMarker marker = file.createMarker(IMarker.PROBLEM);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			int lineNumber = exception.getProblemMark().getLine() + 2;
			marker.setAttribute(IMarker.MESSAGE,exception.getProblem());
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber );
			marker.setAttribute(IMarker.CHAR_START, lastOffset);
			marker.setAttribute(IMarker.CHAR_END, lastOffset+1);
			marker.getAttributes();
		} 
		catch (CoreException ex) {
			ex.printStackTrace();
		}
		
		
	}

	private Map readSchemaFile(String extract) 
	{
		Map schemaMap = null;
		try
		{
			StringReader stringReader = new StringReader(extract);
			reader = new BufferedReader(stringReader);
			String firstLine = reader.readLine();
			if(firstLine != null)
			{
				String[] splits = firstLine.split("\\s");
				if(splits[0].equalsIgnoreCase("#SCHEMA"))
				{
					InputStream schemaInputStream = null;
					String type = splits[1];
					String pathInfo = "";
					if(type.equals("URL"))
					{
						pathInfo = splits[2];
						schemaInputStream =  new URL(pathInfo).openStream();
					}
					else
					{
						if(type.equals("FILE"))
						{
							pathInfo = splits[2];
						}
						else
						{
							pathInfo = type;
						}
						schemaInputStream = new FileInputStream(pathInfo);
					}
					
					
					if(schemaPath.equals(pathInfo)) //Path Info of different types won't look same
					{
						schemaMap = schema;
					}
					else
					{
						schemaPath = pathInfo;
						schemaMap = (Map) schemaParser.load(schemaInputStream);
					}
					
				}
			}
		}
		catch(IOException ex)
		{
			System.out.println("Error Reading Schema \n"+ex.getMessage());
		}
		return schemaMap;
	}


	private void setWorkbenchStatus(final String message) 
	{
		System.out.println(message);
		
		final Display display = Display.getDefault();

		new Thread() 
		{

			public void run() 
			{
	
				display.syncExec(new Runnable() {
					public void run() {
			
						IWorkbench wb = PlatformUI.getWorkbench();
						IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
				
						IWorkbenchPage page = win.getActivePage();
				
						IWorkbenchPart part = page.getActivePart();
						IWorkbenchPartSite site = part.getSite();
				
						EditorSite vSite = ( EditorSite ) site;
				
						IActionBars actionBars =  vSite.getActionBars();
				
						if( actionBars == null )
						return ;
				
						IStatusLineManager statusLineManager =
						 actionBars.getStatusLineManager();
				
						if( statusLineManager == null )
						return ;
				
						statusLineManager.setMessage( message );
					}
				});
			}
		}.start();
	}
	
	private String getIndent(String extract) 
	{
		String indent = "";
		for(int idx = extract.lastIndexOf('\n') + 1;idx<extract.length() && extract.charAt(idx)== ' ';idx++)
		{
			indent+= " ";
		}
		return indent;
	}

	List<ICompletionProposal> getProposals(ListIterator path,Object extracted,int offset, String indent)
	{
		List<ICompletionProposal> list = new ArrayList<ICompletionProposal>(); 
		
		//Object node = tracePathInSchema(path,leaf,schema);
		Object node = schema;
		Object edge = null;
		Object defaultNode = null;
		String unknownEdge = null;
		String leaf = parser.getLeaf();
		while(path.hasNext())
		{
			edge=path.next();
			if(edge instanceof String)
			{
				if(edge.equals("#map"))
				{
					node = ((Map)node).get("mapping");
				}
				else if(edge.equals("#seq"))
				{
					node = ((Map)node).get("sequence");
				}
				else if(edge.equals("#value"))
				{
					if(unknownEdge != null)
					{
						leaf = unknownEdge;
						edge = "#map";
					}
					break;
				}
				else
				{
					if(((Map)node).containsKey(edge))
					{
						node = ((Map)node).get(edge);
					}
					else
					{
						unknownEdge = edge.toString();
						continue;
					}
					edge = "#value";
				}
			}
			else if(edge instanceof Integer)
			{
				try
				{
					Map firstListNode = (Map)((List)node).get(0);
					if(firstListNode!=null && !firstListNode.get("type").equals("any"))
					{
						node = firstListNode;
					}
					else
					{
						node = ((List)node).get((Integer)edge);
					}
				}
				catch(IndexOutOfBoundsException ex)
				{
					node = null;
				}
			}
			
			if(defaultNode!=null && (node == null || (node!=null &&  ( (node instanceof Map && ((Map)node).isEmpty()) || (node instanceof List && ((List)node).isEmpty())  ) ) ) )
			{
				node = defaultNode;
			}
			
			if(node instanceof Map && (edge.equals("#value") || !(edge.toString()).startsWith("#")) )
			{
				defaultNode = ((Map)node).get("default");
			}
			
		}
		
		Iterator keyIter = null;
		
		if(!(edge instanceof Integer) && !edge.equals("#value"))
		{
			Set keyset = ((Map)node).keySet();
			if(keyset!=null && keyset.size()==1 && keyset.contains(leaf) && ((Map)node).get(leaf) instanceof Map)
			{
				keyset = ((Map)((Map)node).get(leaf)).keySet();
				node = ((Map)node).get(leaf);
				leaf="";
			}
			keyIter = keyset.iterator();
			while(keyIter.hasNext())
			{
				String next = (String) keyIter.next();
				Object suggestion = null;
				if(node instanceof Map)
				{
					suggestion = ((Map)node).get(next);
				}
				if(isUnique(next,extracted) && next.startsWith(leaf) && qualifiesOnlyWhenCondition(suggestion,extracted))
				{
					System.out.println(next);
					
					String replacementString = next;
					int overwriteLength = leaf.length();
					int replaceStartsAt = offset-overwriteLength;
					int cursorPosFromReplaceStart = next.length();
					String displayName = next;
					String extraInfo = null;
					
					if(suggestion instanceof Map)
					{
						String type = (String) ((Map)suggestion).get("type");
						if(type!=null && type.equals("map"))
						{
							replacementString+= ": ";
							cursorPosFromReplaceStart += 2;
						}
						else if(type!=null && type.equals("seq"))
						{
							replacementString+= ": ";
							cursorPosFromReplaceStart += 2;
						}
//						else if(type!=null && type.equals("str"))
//						{
//							replacementString+=": ";
//							cursorPosFromReplaceStart += 2;
//						}
//						else if(((Map)suggestion).size()>1)
//						{
//							replacementString+=": ";
//							cursorPosFromReplaceStart += 2;
//						}
						else
						{
							replacementString+=": ";
							cursorPosFromReplaceStart += 2;
						}
						extraInfo = (String) ((Map)suggestion).get("info");
					}
					else
					{
						replacementString+=": ";
						cursorPosFromReplaceStart += 2;
					}
					
					list.add(new CompletionProposal(replacementString, replaceStartsAt,overwriteLength , cursorPosFromReplaceStart,null,displayName,null,extraInfo));
				}
			}
			node = null;
		}
		
		if(node==null)
		{
			node = defaultNode;
		}
		
		if(list.isEmpty() && node!=null)
		{
			list.addAll(suggestByType(node,leaf, offset));
		}
		
		
		return list;
		
	}

	private List<ICompletionProposal> suggestByType(Object node,String leaf, int offset) 
	{
		List<ICompletionProposal> list = new ArrayList<ICompletionProposal>();
		if(node instanceof Map)
		{
			String type = (String) ((Map)node).get("type");
			if(type.equals("map"))
			{
				String replacementString = "{  }";
				String displayName = "{..}";
				String extraInfo = "Insert an empty Map";
				int overwriteLength = 0;
				int replaceStartsAt = offset-overwriteLength;
				int cursorPosFromReplaceStart = 2;
				list.add(new CompletionProposal(replacementString, replaceStartsAt,overwriteLength , cursorPosFromReplaceStart,null,displayName,null,extraInfo));
			}
			else if(type.equals("seq"))
			{
				String replacementString = "[  ]";
				String displayName = "[..]";
				String extraInfo = "Insert an empty List";
				int overwriteLength = 0;
				int replaceStartsAt = offset-overwriteLength;
				int cursorPosFromReplaceStart = 2;
				list.add(new CompletionProposal(replacementString, replaceStartsAt,overwriteLength , cursorPosFromReplaceStart,null,displayName,null,extraInfo));
			}
			else if(type.equals("str") && ((Map)node).get("enum")!=null)
			{
				Map enumMap = (Map)((Map)node).get("enum");
				for(Object value: enumMap.keySet())
				{
					String enumVal = (String) value;
					if(enumVal.startsWith(leaf))
					{
						System.out.println(enumVal);
						String replacementString = enumVal;
						String extraInfo = null;
						int overwriteLength = leaf.length();
						int replaceStartsAt = offset-overwriteLength;
						int cursorPosFromReplaceStart = replacementString.length();
						if(enumMap.get(value) instanceof Map)
						{
							extraInfo = (String) ((Map)enumMap.get(value)).get("info");
						}
						list.add(new CompletionProposal(replacementString, replaceStartsAt,overwriteLength , cursorPosFromReplaceStart,null,enumVal,null,extraInfo));
					}
				}
			}
				
		}
		else if(node instanceof String) //Assume type to be a non-enummed String 
		{
			String displayName = node.toString();
			if(displayName.startsWith(leaf))
			{
				String replacementString = displayName+ ": ";
				String extraInfo = null;
				int overwriteLength = leaf.length();
				int replaceStartsAt = offset-overwriteLength;
				int cursorPosFromReplaceStart = replacementString.length();
				list.add(new CompletionProposal(replacementString, replaceStartsAt,overwriteLength , cursorPosFromReplaceStart,null,displayName,null,extraInfo));
			}
		}
		return list;
	}

	private boolean isUnique(String key, Object extracted) 
	{
		if(extracted instanceof Map)
		{
			ArrayList<String> path = new ArrayList<String>();
			for(int i=2;i<parser.getPath().size();i++)
			{
				Object node = parser.getPath().get(i);
				if(node instanceof String && !((String)node).startsWith("#"))
				{
					path.add((String) node);
				}
				else if(node instanceof Integer)
				{
					path.add("#"+node);
				}
			}
			String[] pathArr = new String[path.size()];;
			pathArr = path.toArray(pathArr);
			Object parent = readMapFromPath(extracted, pathArr);
			if(parent instanceof Map && ((Map)parent).containsKey(key))
			{
				return false;
			}
		}
		return true;
	}

	private boolean qualifiesOnlyWhenCondition(Object suggestion, Object extracted) {
		
		if(suggestion!= null && suggestion instanceof Map && ((Map)suggestion).get("onlyWhen")!=null)
		{
			List onlyWhen = (List)((Map)suggestion).get("onlyWhen");
			for(Object condition: onlyWhen )
			{
				String fromSchema = (String) ((List)condition).get(1);
				String fromExtract = null;
				try 
				{
					fromExtract = readExtract(extracted,((List)condition).get(0));
				} 
				catch (Exception e) 
				{
					e.printStackTrace();
				}
				if(!fromSchema.equals(fromExtract))
				{
					return false;
				}
			}
		}
		return true;
	}

	private String readExtract(Object extracted, Object key) throws Exception {
		Object value = extracted;
		try
		{
			String[] edges = ((String)key).split("\\.");
			edges = absPath(edges);
			value = readMapFromPath(value, edges);
			return (String) value;
		}
		catch(Exception ex)
		{
			throw new Exception("Invalid path to read from extracted", ex);
		}
		
	}

	private Object readMapFromPath(Object extracted, String[] edges) {
		if(edges == null)
		{
			return extracted;
		}
		Object node = extracted;
		for(String edge:edges)
		{
			if(edge.startsWith("#"))
			{
				node = ((List)node).get(Integer.parseInt(edge.substring(1))-1);
			}
			else
			{
				node = ((Map)node).get(edge);
			}
		}
		return node;
	}

	private String[] absPath(String[] edges) {
		List<String> absPath = new ArrayList<String>();
		List path = parser.getPath();
		int up = 0;
		int i;
		if(edges[0].equals("#path"))
		{
			for(i=1;i<edges.length;i++)
			{
				if(edges[i].equals("#parent"))
				{
					up++;
				}
				else
				{
					break;
				}
			}
			int pathLength = (path.size() - up) + (edges.length - up - 1 );
			int idx = 1;
			for (; idx < (path.size() - up); idx++) 
			{
				Object edge = path.get(idx);
				
				if(edge instanceof Integer)
				{
					absPath.add("#"+(Integer)edge);
				}
				else
				{
					if(((String) edge).startsWith("#")) continue;
					absPath.add((String)edge);
				}
			}
			for(;i<edges.length;i++)
			{
				absPath.add((String)edges[i]);
			}
		}
		String[] pathArr = new String[absPath.size()];
		return absPath.toArray(pathArr);
	}

}
