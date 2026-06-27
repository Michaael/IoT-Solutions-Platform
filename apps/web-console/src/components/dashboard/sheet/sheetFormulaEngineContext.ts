export interface IspfFormulaContext {
  bindingValues: Map<string, number | string | boolean>;
  tableColumnSums: Map<string, number>;
  histValues: Map<string, number>;
}

let ispfFormulaContext: IspfFormulaContext = {
  bindingValues: new Map(),
  tableColumnSums: new Map(),
  histValues: new Map(),
};

export function setIspfFormulaContext(ctx: IspfFormulaContext): void {
  ispfFormulaContext = ctx;
}

export function getIspfFormulaContext(): IspfFormulaContext {
  return ispfFormulaContext;
}

export function bindingCacheKey(path: string, varName: string, field = "value"): string {
  return `${path}|${varName}|${field}`;
}

export function histCacheKey(path: string, varName: string, minutes: number): string {
  return `${path}|${varName}|${minutes}`;
}
