require "spec_helper"
require "webmachine/spec/adapter_lint"
require "webmachine/adapters/ring"

describe Webmachine::Adapters::Ring do
  it_should_behave_like :adapter_lint
end
