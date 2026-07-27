export type JsonRenderElementProps = Record<string, unknown>;

export type JsonRenderElement = {
  type: string;
  props: JsonRenderElementProps;
  children: string[];
  visible?: unknown;
};

export type JsonRenderSpec = {
  root: string;
  elements: Record<string, JsonRenderElement>;
};

export const jsonRenderSpec: JsonRenderSpec = {
  root: 'safeArea',
  elements: {
    safeArea: {
      type: 'SafeArea',
      props: {
        backgroundColor: '#0B1220',
      },
      children: ['title', 'detail'],
    },
    title: {
      type: 'Heading',
      props: {
        text: 'U40R React Native spike',
        level: 'h2',
        color: '#EEF2FF',
        align: 'center',
      },
      children: [],
    },
    detail: {
      type: 'Paragraph',
      props: {
        text: 'Expo 57 + RN 0.86 compatibility probe',
        align: 'center',
      },
      children: [],
    },
  },
};

export const jsonRenderSpecPatches =
  '{"op":"add","path":"/root","value":"safeArea"}\n' +
  '{"op":"add","path":"/elements/safeArea","value":{"type":"SafeArea","props":{"backgroundColor":"#0B1220"},"children":["title","detail"]}}\n' +
  '{"op":"add","path":"/elements/title","value":{"type":"Heading","props":{"text":"U40R React Native spike","level":"h2","color":"#EEF2FF","align":"center"},"children":[]}}\n' +
  '{"op":"add","path":"/elements/detail","value":{"type":"Paragraph","props":{"text":"Expo 57 + RN 0.86 compatibility probe","align":"center"},"children":[]}}';
