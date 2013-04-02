package org.dadacoalition.yedit.template;

import java.util.List;

import org.dadacoalition.yedit.Activator;
import org.dadacoalition.yedit.YEditLog;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateCompletionProcessor;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.swt.graphics.Image;

import shubhamm.yedit.contentassist.YAMLSchemaCompletionProcessor;

public class YEditCompletionProcessor extends TemplateCompletionProcessor
{
  private YAMLSchemaCompletionProcessor schemaProposer;

  public YEditCompletionProcessor()
  {
    this.schemaProposer = new YAMLSchemaCompletionProcessor();
  }

  protected TemplateContextType getContextType(ITextViewer viewer, IRegion region)
  {
    YEditLog.logger.info("called getContextType");
    return Activator.getDefault().getContextTypeRegistry().getContextType("org.dadacoalition.yedit.template.yaml");
  }

  protected Image getImage(Template template) {
    return null;
  }

  protected Template[] getTemplates(String contextTypeId)
  {
    YEditLog.logger.info("called getTemplates");
    Template[] templates = Activator.getDefault().getTemplateStore().getTemplates();
    YEditTemplate[] yeditTemplates = new YEditTemplate[templates.length];

    for (int i = 0; i < templates.length; i++) {
      yeditTemplates[i] = new YEditTemplate(templates[i]);
    }
    return yeditTemplates;
  }

  public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset)
  {
    List<ICompletionProposal> schemaProposals = this.schemaProposer.computeCompletionProposalsFromSchema(viewer, offset);
    ICompletionProposal[] templateProposals = super.computeCompletionProposals(viewer, offset);
    ICompletionProposal[] proposals = new ICompletionProposal[schemaProposals.size() + templateProposals.length];
    int index = 0;
    for (ICompletionProposal proposal : schemaProposals)
    {
      proposals[(index++)] = proposal;
    }
    for (ICompletionProposal proposal : templateProposals)
    {
      proposals[(index++)] = proposal;
    }
    return proposals;
  }
}