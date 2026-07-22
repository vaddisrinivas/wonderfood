import { runWorkflow as runMcpWorkflow, WorkflowExecutionResult as McpWorkflowExecutionResult } from '../mcp/tools';

export type WorkflowExecutionResult = McpWorkflowExecutionResult;

export const runWorkflow = runMcpWorkflow;
